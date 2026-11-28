#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace player2::dsp {

/**
 * Lock-free single-producer audio capture ring + lightweight radix-2 FFT spectrum
 * analyzer for the optional Settings-controlled live frequency visualizer.
 *
 * Audio thread overhead when disabled: one relaxed atomic load (zero branches inside inner loops).
 * Audio thread overhead when enabled: minimal atomic memcpy into 512-slot seqlock ring.
 */
class SpectrumAnalyzer {
public:
    static constexpr std::size_t kFftSize = 512;
    static constexpr std::size_t kMaxBands = 32;

private:
    std::atomic<bool> enabled_{false};
    std::atomic<std::uint64_t> seq_{0};
    std::atomic<int> sampleRate_{48000};
    std::atomic<std::size_t> cursor_{0};
    std::array<std::atomic<std::uint32_t>, kFftSize> samples_{};

    static inline std::uint32_t floatToBits(float v) noexcept {
        std::uint32_t b = 0;
        std::memcpy(&b, &v, sizeof(b));
        return b;
    }

    static inline float bitsToFloat(std::uint32_t b) noexcept {
        float v = 0.0f;
        std::memcpy(&v, &b, sizeof(v));
        return std::isfinite(v) ? v : 0.0f;
    }

    static void fft512(std::array<float, kFftSize>& re, std::array<float, kFftSize>& im) noexcept {
        // Bit-reversal permutation
        std::size_t j = 0;
        for (std::size_t i = 0; i < kFftSize; ++i) {
            if (i < j) {
                std::swap(re[i], re[j]);
                std::swap(im[i], im[j]);
            }
            std::size_t bit = kFftSize >> 1;
            while (bit != 0 && (j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j |= bit;
        }

        constexpr double kPi = 3.14159265358979323846;
        for (std::size_t len = 2; len <= kFftSize; len <<= 1) {
            const double angle = -2.0 * kPi / static_cast<double>(len);
            const float wLenRe = static_cast<float>(std::cos(angle));
            const float wLenIm = static_cast<float>(std::sin(angle));
            const std::size_t half = len >> 1;
            for (std::size_t start = 0; start < kFftSize; start += len) {
                float wRe = 1.0f;
                float wIm = 0.0f;
                for (std::size_t k = 0; k < half; ++k) {
                    const std::size_t even = start + k;
                    const std::size_t odd = even + half;
                    const float tRe = wRe * re[odd] - wIm * im[odd];
                    const float tIm = wRe * im[odd] + wIm * re[odd];
                    re[odd] = re[even] - tRe;
                    im[odd] = im[even] - tIm;
                    re[even] += tRe;
                    im[even] += tIm;
                    const float nextRe = wRe * wLenRe - wIm * wLenIm;
                    wIm = wRe * wLenIm + wIm * wLenRe;
                    wRe = nextRe;
                }
            }
        }
    }

public:
    SpectrumAnalyzer() noexcept {
        for (auto& s : samples_) s.store(0u, std::memory_order_relaxed);
    }

    void setEnabled(bool enabled) noexcept {
        enabled_.store(enabled, std::memory_order_relaxed);
    }

    bool isEnabled() const noexcept {
        return enabled_.load(std::memory_order_relaxed);
    }

    void push(const float* stereo, std::size_t frames, int rate) noexcept {
        if (!enabled_.load(std::memory_order_relaxed) || !stereo || frames == 0 || rate <= 0) return;

        seq_.fetch_add(1, std::memory_order_acq_rel);
        std::atomic_thread_fence(std::memory_order_release);
        sampleRate_.store(rate, std::memory_order_relaxed);

        const std::size_t start = frames > kFftSize ? (frames - kFftSize) : 0;
        std::size_t pos = cursor_.load(std::memory_order_relaxed);
        for (std::size_t n = start; n < frames; ++n) {
            const float mono = 0.5f * (stereo[n * 2] + stereo[n * 2 + 1]);
            samples_[pos & (kFftSize - 1)].store(floatToBits(mono), std::memory_order_relaxed);
            ++pos;
        }
        cursor_.store(pos & (kFftSize - 1), std::memory_order_relaxed);
        seq_.fetch_add(1, std::memory_order_release);
    }

    void reset() noexcept {
        seq_.fetch_add(1, std::memory_order_acq_rel);
        std::atomic_thread_fence(std::memory_order_release);
        for (auto& s : samples_) s.store(0u, std::memory_order_relaxed);
        cursor_.store(0, std::memory_order_relaxed);
        seq_.fetch_add(1, std::memory_order_release);
    }

    bool computeBands(float* outBands, std::size_t bandCount) const noexcept {
        if (!outBands || bandCount == 0 || bandCount > kMaxBands) return false;
        std::fill(outBands, outBands + bandCount, 0.0f);
        if (!enabled_.load(std::memory_order_relaxed)) return true;

        std::array<float, kFftSize> re{};
        std::array<float, kFftSize> im{};
        int rate = 48000;
        bool readOk = false;

        for (int attempt = 0; attempt < 8; ++attempt) {
            const auto before = seq_.load(std::memory_order_acquire);
            if ((before & 1ULL) != 0ULL) continue;
            rate = sampleRate_.load(std::memory_order_relaxed);
            const std::size_t head = cursor_.load(std::memory_order_relaxed);
            for (std::size_t i = 0; i < kFftSize; ++i) {
                const auto bits = samples_[(head + i) & (kFftSize - 1)].load(std::memory_order_relaxed);
                re[i] = bitsToFloat(bits);
            }
            std::atomic_thread_fence(std::memory_order_acquire);
            if (seq_.load(std::memory_order_relaxed) == before) {
                readOk = true;
                break;
            }
        }
        if (!readOk || rate <= 0) return false;

        float peakTime = 0.0f;
        constexpr double kTwoPi = 6.28318530717958647692;
        for (std::size_t i = 0; i < kFftSize; ++i) {
            peakTime = std::max(peakTime, std::abs(re[i]));
            const float w = static_cast<float>(0.5 * (1.0 - std::cos(kTwoPi * static_cast<double>(i) / static_cast<double>(kFftSize - 1))));
            re[i] *= w;
            im[i] = 0.0f;
        }
        if (peakTime < 1e-6f) return true;

        fft512(re, im);

        const std::size_t nyquistBins = kFftSize / 2;
        const double binHz = static_cast<double>(rate) / static_cast<double>(kFftSize);
        const double fMin = 45.0;
        const double fMax = std::min(15500.0, static_cast<double>(rate) * 0.45);
        const double ratio = fMax / fMin;
        // Coherent gain compensation for Hann FFT (~N / 4)
        const double invScale = 4.0 / static_cast<double>(kFftSize);

        for (std::size_t b = 0; b < bandCount; ++b) {
            const double loHz = fMin * std::pow(ratio, static_cast<double>(b) / static_cast<double>(bandCount));
            const double hiHz = fMin * std::pow(ratio, static_cast<double>(b + 1) / static_cast<double>(bandCount));
            std::size_t binLo = std::clamp<std::size_t>(static_cast<std::size_t>(std::floor(loHz / binHz)), 1, nyquistBins - 1);
            std::size_t binHi = std::clamp<std::size_t>(static_cast<std::size_t>(std::ceil(hiHz / binHz)), binLo, nyquistBins - 1);

            double sumSq = 0.0;
            std::size_t count = 0;
            for (std::size_t k = binLo; k <= binHi; ++k) {
                const double mag = std::hypot(static_cast<double>(re[k]), static_cast<double>(im[k])) * invScale;
                sumSq += mag * mag;
                ++count;
            }
            const double rms = std::sqrt(sumSq / static_cast<double>(std::max<std::size_t>(1, count)));
            const double db = 20.0 * std::log10(std::max(rms, 1e-5));
            // Map [-56 dBFS .. -4 dBFS] cleanly onto [0 .. 1]
            const float norm = static_cast<float>(std::clamp((db + 56.0) / 52.0, 0.0, 1.0));
            outBands[b] = norm;
        }
        return true;
    }
};

} // namespace player2::dsp
