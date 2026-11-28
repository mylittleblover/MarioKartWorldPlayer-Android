#include "api/AudioSourceApi.hpp"
#include "api/EngineApi.hpp"
#include "api/StreamTypes.hpp"
#include "audio/source/AudioFrameSource.hpp"
#include "decoder/NintendoDsp.hpp"
#include "runtime/EngineRuntime.hpp"
#include <opus.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <memory>
#include <vector>

namespace {
using namespace player2;

void putLe(std::vector<std::uint8_t>& buf, std::size_t at, std::uint32_t value, int bytes = 4) {
    for (int i = 0; i < bytes; ++i) {
        buf[at + static_cast<std::size_t>(i)] = static_cast<std::uint8_t>((value >> (8 * i)) & 0xffu);
    }
}

std::vector<std::uint8_t> buildPcm16Bwav(
    int sampleRate,
    const std::vector<std::uint8_t>& speakers,
    const std::vector<std::vector<std::int16_t>>& channelSamples,
    std::int64_t loopStart = -1,
    std::int64_t loopEnd = -1
) {
    const std::size_t channels = speakers.size();
    const std::size_t frames = channelSamples.front().size();
    const std::size_t headerSize = 16 + channels * 76;
    const std::size_t alignedDataStart = (headerSize + 63u) & ~std::size_t{63u};
    const std::size_t channelBytes = frames * 2;
    const std::size_t channelStride = (channelBytes + 63u) & ~std::size_t{63u};
    const std::size_t totalSize = alignedDataStart + (channels - 1) * channelStride + channelBytes;

    std::vector<std::uint8_t> out(totalSize, 0);
    out[0] = 'B'; out[1] = 'W'; out[2] = 'A'; out[3] = 'V';
    putLe(out, 4, 0xfeffu, 2);
    putLe(out, 6, 1u, 2);
    putLe(out, 12, 0u, 2);
    putLe(out, 14, static_cast<std::uint32_t>(channels), 2);

    for (std::size_t ch = 0; ch < channels; ++ch) {
        const std::size_t h = 16 + ch * 76;
        const std::size_t start = alignedDataStart + ch * channelStride;
        putLe(out, h + 0, 0u, 2); // codec 0 = PCM16
        putLe(out, h + 2, speakers[ch], 2);
        putLe(out, h + 4, static_cast<std::uint32_t>(sampleRate), 4);
        putLe(out, h + 8, static_cast<std::uint32_t>(frames), 4);
        putLe(out, h + 12, static_cast<std::uint32_t>(frames), 4);
        putLe(out, h + 48, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 52, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 56, 1u, 4);
        if (loopStart >= 0 && loopEnd > loopStart) {
            putLe(out, h + 60, static_cast<std::uint32_t>(loopEnd), 4);
            putLe(out, h + 64, static_cast<std::uint32_t>(loopStart), 4);
        } else {
            putLe(out, h + 60, 0xffffffffu, 4);
            putLe(out, h + 64, 0u, 4);
        }
        for (std::size_t i = 0; i < frames; ++i) {
            const auto u = static_cast<std::uint16_t>(channelSamples[ch][i]);
            putLe(out, start + i * 2, u, 2);
        }
    }
    return out;
}

std::vector<std::uint8_t> buildDspBwav(
    int sampleRate,
    std::size_t channels,
    std::size_t frames,
    std::vector<audio::NintendoDspChannel>& outChannels,
    std::vector<std::vector<std::uint8_t>>& outBlocks
) {
    const std::size_t blocks = (frames + 13) / 14;
    const std::size_t channelBytes = blocks * 8;
    const std::size_t channelStride = (channelBytes + 63u) & ~std::size_t{63u};
    const std::size_t headerSize = 16 + channels * 76;
    const std::size_t alignedStart = (headerSize + 63u) & ~std::size_t{63u};
    const std::size_t totalSize = alignedStart + (channels - 1) * channelStride + channelBytes;

    std::vector<std::uint8_t> out(totalSize, 0);
    out[0] = 'B'; out[1] = 'W'; out[2] = 'A'; out[3] = 'V';
    putLe(out, 4, 0xfeffu, 2);
    putLe(out, 6, 1u, 2);
    putLe(out, 12, 0u, 2);
    putLe(out, 14, static_cast<std::uint32_t>(channels), 2);

    outChannels.resize(channels);
    outBlocks.resize(channels);
    for (std::size_t ch = 0; ch < channels; ++ch) {
        const std::size_t h = 16 + ch * 76;
        const std::size_t start = alignedStart + ch * channelStride;
        putLe(out, h + 0, 1u, 2); // codec 1 = DSP-ADPCM
        putLe(out, h + 2, static_cast<std::uint32_t>(ch), 2);
        putLe(out, h + 4, static_cast<std::uint32_t>(sampleRate), 4);
        putLe(out, h + 8, static_cast<std::uint32_t>(frames), 4);
        putLe(out, h + 12, static_cast<std::uint32_t>(frames), 4);
        const std::array<std::int16_t, 16> coefs{
            1800, -700, 1200, -300, 2000, -900, 500, 100,
            1500, -500, 900, -100, 1600, -600, 300, 0
        };
        for (std::size_t k = 0; k < 16; ++k) {
            outChannels[ch].coefficients[k] = coefs[k];
            putLe(out, h + 16 + k * 2, static_cast<std::uint16_t>(coefs[k]), 2);
        }
        outChannels[ch].initial = {static_cast<std::int32_t>(100 + ch * 50), static_cast<std::int32_t>(-40 - ch * 25)};
        putLe(out, h + 48, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 52, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 56, 1u, 4);
        putLe(out, h + 60, 0xffffffffu, 4);
        putLe(out, h + 64, 0u, 4);
        putLe(out, h + 68, 0x12u, 2);
        putLe(out, h + 70, static_cast<std::uint16_t>(outChannels[ch].initial.previous), 2);
        putLe(out, h + 72, static_cast<std::uint16_t>(outChannels[ch].initial.older), 2);

        outBlocks[ch].resize(channelBytes);
        std::uint32_t rng = 0x12345678u + static_cast<std::uint32_t>(ch * 997u);
        for (std::size_t b = 0; b < blocks; ++b) {
            rng = rng * 1664525u + 1013904223u;
            const std::uint8_t predictor = static_cast<std::uint8_t>((rng >> 24) & 7u);
            const std::uint8_t scale = static_cast<std::uint8_t>((rng >> 20) & 5u);
            outBlocks[ch][b * 8] = static_cast<std::uint8_t>((predictor << 4) | scale);
            for (std::size_t byte = 1; byte < 8; ++byte) {
                rng = rng * 1664525u + 1013904223u;
                outBlocks[ch][b * 8 + byte] = static_cast<std::uint8_t>((rng >> 16) & 0xffu);
            }
        }
        std::copy(outBlocks[ch].begin(), outBlocks[ch].end(), out.begin() + static_cast<std::ptrdiff_t>(start));
    }
    return out;
}

class CountingMemoryInput final : public audio::EncodedSourceInput {
    std::vector<std::uint8_t> data_;
    std::size_t* bytesRead_;
    std::size_t* readCalls_;
public:
    CountingMemoryInput(std::vector<std::uint8_t> data, std::size_t* bytesRead, std::size_t* readCalls)
        : data_(std::move(data)), bytesRead_(bytesRead), readCalls_(readCalls) {}
    std::uint64_t byteLength() const noexcept override { return data_.size(); }
    audio::SourceReadResult read(audio::SourceByteOffset offset, std::uint8_t* destination, std::size_t count) noexcept override {
        if (offset.value > data_.size() || count > data_.size() - offset.value) {
            return {audio::SourceStatus::MalformedSource, 0};
        }
        if (bytesRead_) *bytesRead_ += count;
        if (readCalls_) ++(*readCalls_);
        std::copy_n(data_.data() + offset.value, count, destination);
        return {audio::SourceStatus::Ok, count};
    }
};

double computeRms(const std::vector<float>& stereo, int ch) {
    double sumSq = 0.0;
    const std::size_t frames = stereo.size() / 2;
    for (std::size_t i = 0; i < frames; ++i) {
        const double v = stereo[i * 2 + static_cast<std::size_t>(ch)];
        sumSq += v * v;
    }
    return std::sqrt(sumSq / static_cast<double>(frames));
}

bool testSurroundDownmix() {
    constexpr int rate = 48000;
    constexpr std::size_t frames = 2048;
    constexpr double kPi = 3.14159265358979323846;

    // 1. Verify v0.1.0-beta01 unity front-channel loudness on 5.1 BWAV (scale_ = 1.0, no +6 dB boost).
    // Equal-level Front L/R in 5.1 (with silent C/LFE/Rear) must match 2.0 stereo within 0.01 dB!
    const double frontAmp = 0.25;
    std::vector<std::vector<std::int16_t>> stereoCh(2, std::vector<std::int16_t>(frames));
    std::vector<std::vector<std::int16_t>> surrCh(6, std::vector<std::int16_t>(frames, 0));
    for (std::size_t i = 0; i < frames; ++i) {
        const double s = std::sin(2.0 * kPi * 440.0 * static_cast<double>(i) / rate);
        const auto sample = static_cast<std::int16_t>(std::llround(s * frontAmp * 32767.0));
        stereoCh[0][i] = sample;
        stereoCh[1][i] = sample;
        surrCh[0][i] = sample;
        surrCh[1][i] = sample;
    }

    auto bwav2 = buildPcm16Bwav(rate, {0, 1}, stereoCh);
    auto bwav51 = buildPcm16Bwav(rate, {0, 1, 2, 3, 4, 5}, surrCh);

    api::SourceOpenRequest req2;
    req2.providerId = "native.bwav";
    req2.bytes = {bwav2.data(), bwav2.size()};
    auto open2 = api::openAudioSource(req2);
    if (open2.status != audio::SourceStatus::Ok) { std::cerr << "Failed to open 2ch BWAV\n"; return false; }

    api::SourceOpenRequest req51;
    req51.providerId = "native.bwav";
    req51.bytes = {bwav51.data(), bwav51.size()};
    auto open51 = api::openAudioSource(req51);
    if (open51.status != audio::SourceStatus::Ok) { std::cerr << "Failed to open 5.1 BWAV\n"; return false; }

    std::vector<float> pcm2(frames * 2), pcm51(frames * 2);
    const auto r2 = api::renderAudioSource(open2.handle, pcm2.data(), frames, pcm2.size());
    const auto r51 = api::renderAudioSource(open51.handle, pcm51.data(), frames, pcm51.size());
    api::closeAudioSource(open2.handle);
    api::closeAudioSource(open51.handle);
    if (r2.status != audio::SourceStatus::Ok || r2.frames != frames ||
        r51.status != audio::SourceStatus::Ok || r51.frames != frames) {
        std::cerr << "renderAudioSource failed on 2ch/5.1 test\n";
        return false;
    }

    const double rms2 = computeRms(pcm2, 0);
    const double rms51 = computeRms(pcm51, 0);
    const double diffDb = 20.0 * std::log10(rms51 / rms2);
    if (!std::isfinite(diffDb) || rms2 <= 0.0 || rms51 <= 0.0 || std::abs(diffDb) > 0.05) {
        std::cerr << "5.1 vs 2.0 beta01 unity parity failed: diffDb=" << diffDb << " rms2=" << rms2 << " rms51=" << rms51 << "\n";
        return false;
    }

    // 2. Verify ITU-R BS.775 weights (C = 1/sqrt(2), LFE = 0.0, Ls/Rs = 1/sqrt(2)) at unity scale 1.0.
    std::vector<std::vector<std::int16_t>> isoCh(6, std::vector<std::int16_t>(frames, 0));
    for (std::size_t i = 0; i < frames; ++i) {
        isoCh[2][i] = 3276;  // Center = ~0.10 -> 0.10 * 1/sqrt(2) on both L & R
        isoCh[3][i] = 16384; // LFE = ~0.50 (must be excluded -> 0.0)
        isoCh[4][i] = 6554;  // Ls = ~0.20 -> adds 0.20 * 1/sqrt(2) to L only
    }
    auto bwavCenter = buildPcm16Bwav(rate, {0, 1, 2, 3, 4, 5}, isoCh);
    api::SourceOpenRequest reqC;
    reqC.providerId = "native.bwav";
    reqC.bytes = {bwavCenter.data(), bwavCenter.size()};
    auto openC = api::openAudioSource(reqC);
    if (openC.status != audio::SourceStatus::Ok) return false;
    std::vector<float> pcmC(frames * 2);
    const auto rC = api::renderAudioSource(openC.handle, pcmC.data(), frames, pcmC.size());
    api::closeAudioSource(openC.handle);
    if (rC.status != audio::SourceStatus::Ok || rC.frames != frames) return false;
    const double expectedL = (3276.0 + 6554.0) / 32768.0 * 0.70710678118654752440;
    const double expectedR = 3276.0 / 32768.0 * 0.70710678118654752440;
    if (std::abs(pcmC[0] - expectedL) > 1e-4 || std::abs(pcmC[1] - expectedR) > 1e-4) {
        std::cerr << "Center/Rear/LFE weight mismatch: got L=" << pcmC[0] << " R=" << pcmC[1]
                  << ", expected L=" << expectedL << " R=" << expectedR << "\n";
        return false;
    }

    // 3. Verify extreme full-scale coherent 5.1 does not clip and preserves stereo ratio.
    std::vector<std::vector<std::int16_t>> hotCh(6, std::vector<std::int16_t>(frames, 0));
    for (std::size_t i = 0; i < frames; ++i) {
        hotCh[0][i] = 30000; // L
        hotCh[1][i] = 15000; // R
        hotCh[2][i] = 20000; // C
        hotCh[4][i] = 25000; // Ls
        hotCh[5][i] = 10000; // Rs
    }
    auto bwavHot = buildPcm16Bwav(rate, {0, 1, 2, 3, 4, 5}, hotCh);
    api::SourceOpenRequest reqHot;
    reqHot.providerId = "native.bwav";
    reqHot.bytes = {bwavHot.data(), bwavHot.size()};
    auto openHot = api::openAudioSource(reqHot);
    if (openHot.status != audio::SourceStatus::Ok) return false;
    std::vector<float> pcmHot(frames * 2);
    const auto rHot = api::renderAudioSource(openHot.handle, pcmHot.data(), frames, pcmHot.size());
    api::closeAudioSource(openHot.handle);
    if (rHot.status != audio::SourceStatus::Ok || rHot.frames != frames) return false;

    const double rawL = (30000.0 + 20000.0 * 0.7071067811865475 + 25000.0 * 0.7071067811865475) / 32768.0;
    const double rawR = (15000.0 + 20000.0 * 0.7071067811865475 + 10000.0 * 0.7071067811865475) / 32768.0;
    const double expectedRatio = rawR / rawL;
    const double actualRatio = static_cast<double>(pcmHot[1]) / static_cast<double>(pcmHot[0]);
    if (!(pcmHot[0] > 0.90f && pcmHot[0] < 0.996f) || std::abs(actualRatio - expectedRatio) > 1e-4) {
        std::cerr << "Soft limiter check failed: L=" << pcmHot[0] << " R=" << pcmHot[1]
                  << " ratio=" << actualRatio << " expectedRatio=" << expectedRatio << "\n";
        return false;
    }

    // 4. Verify 4.0 quad speaker-role BWAV keeps unity scale 1.0.
    std::vector<std::vector<std::int16_t>> quadCh(4, std::vector<std::int16_t>(frames, 0));
    for (std::size_t i = 0; i < frames; ++i) quadCh[0][i] = 16384;
    auto bwavQuad = buildPcm16Bwav(rate, {0, 1, 4, 5}, quadCh);
    api::SourceOpenRequest reqQ;
    reqQ.providerId = "native.bwav";
    reqQ.bytes = {bwavQuad.data(), bwavQuad.size()};
    auto openQ = api::openAudioSource(reqQ);
    if (openQ.status != audio::SourceStatus::Ok) return false;
    std::vector<float> pcmQ(frames * 2);
    const auto rQ = api::renderAudioSource(openQ.handle, pcmQ.data(), frames, pcmQ.size());
    api::closeAudioSource(openQ.handle);
    if (rQ.status != audio::SourceStatus::Ok || rQ.frames != frames) return false;
    if (std::abs(pcmQ[0] - 0.5f) > 1e-4f || std::abs(pcmQ[1]) > 1e-6f) {
        std::cerr << "4.0 quad unity check failed: L=" << pcmQ[0] << " R=" << pcmQ[1] << "\n";
        return false;
    }

    std::cout << "PASS: 5.1 surround beta01 unity parity (diffDb=" << diffDb << " dB), BS.775 weights, LFE=0, soft limiter, 4.0 unity\n";
    return true;
}

bool testLazyBwavDspAndSeek() {
    constexpr int rate = 48000;
    constexpr std::size_t channels = 2;
    constexpr std::size_t frames = 1000000;
    std::vector<audio::NintendoDspChannel> dspCh;
    std::vector<std::vector<std::uint8_t>> blocks;
    auto bwav = buildDspBwav(rate, channels, frames, dspCh, blocks);

    std::size_t bytesRead = 0;
    std::size_t readCalls = 0;
    auto input = std::make_unique<CountingMemoryInput>(bwav, &bytesRead, &readCalls);
    audio::DecodeLimits limits;
    auto source = audio::openBwav(std::move(input), limits);
    if (!source) { std::cerr << "openBwav failed\n"; return false; }

    // Lazy open must NOT scan/decode the full ~1.14 MB file; only header & gap-check windows
    // (at most 3 cache windows = <= 196,608 bytes) should be touched on open.
    const std::size_t bytesOnOpen = bytesRead;
    const std::size_t callsOnOpen = readCalls;
    if (bytesOnOpen > 3 * 65536 || callsOnOpen > 3) {
        std::cerr << "openBwav eagerly read too many bytes on open: bytes=" << bytesOnOpen << " calls=" << callsOnOpen << "\n";
        return false;
    }

    // Reference scalar decode using dspSample() directly.
    std::vector<float> reference(frames * channels);
    std::array<audio::DspHistory, 2> refHist{dspCh[0].initial, dspCh[1].initial};
    for (std::size_t f = 0; f < frames; ++f) {
        for (std::size_t ch = 0; ch < channels; ++ch) {
            const std::size_t p = (f / 14) * 8;
            const std::size_t within = f % 14;
            const std::uint8_t header = blocks[ch][p];
            const std::uint8_t packed = blocks[ch][p + 1 + within / 2];
            const int nibble = (within % 2) ? (packed & 15) : (packed >> 4);
            reference[f * channels + ch] = static_cast<float>(audio::dspSample(header, nibble, dspCh[ch], refHist[ch])) / 32768.0f;
        }
    }

    // Random seeks BEFORE sequential decode (testing lazy forward & backward checkpointing).
    const std::array<std::int64_t, 5> targets{150000, 42000, 199000, 13, 0};
    std::vector<float> slice(256 * channels);
    for (const auto target : targets) {
        if (source->seek({target}) != audio::SourceStatus::Ok) {
            std::cerr << "BwavSource seek failed at " << target << "\n";
            return false;
        }
        const auto got = source->render(slice.data(), 256);
        if (got != 256) {
            std::cerr << "BwavSource render short at " << target << "\n";
            return false;
        }
        for (std::size_t i = 0; i < 256 * channels; ++i) {
            const float expected = reference[static_cast<std::size_t>(target) * channels + i];
            if (slice[i] != expected) {
                std::cerr << "Bit-exact DSP mismatch at target=" << target << " idx=" << i
                          << " got=" << slice[i] << " expected=" << expected << "\n";
                return false;
            }
        }
    }

    // Full sequential render bit-exactness check.
    source->reset();
    std::vector<float> decoded(frames * channels);
    const auto fullGot = source->render(decoded.data(), frames);
    if (fullGot != frames || decoded != reference) {
        std::cerr << "Full sequential block DSP decode mismatch\n";
        return false;
    }

    std::cout << "PASS: BwavSource lazy open (bytesOnOpen=" << bytesOnOpen << ", callsOnOpen=" << callsOnOpen << "), bit-exact block DSP decode & random seek\n";
    return true;
}

bool testLinearFadeWindowAndSeekWarm() {
    constexpr int rate = 48000;
    constexpr std::size_t baseFrames = 32000;
    constexpr std::int64_t loopStart = 4000;
    constexpr std::int64_t loopEnd = 32000; // loopLen = 28000 < fadeOut 40000!

    std::vector<std::vector<std::int16_t>> ch(2, std::vector<std::int16_t>(baseFrames, 16384)); // constant +0.5
    auto bwav = buildPcm16Bwav(rate, {0, 1}, ch, loopStart, loopEnd);

    auto* engine = api::createEngine();
    if (!engine) return false;
    api::SourceOpenRequest req;
    req.providerId = "native.bwav";
    req.assetId = "loop-test";
    req.bytes = {bwav.data(), bwav.size()};
    if (api::loadLinearSource(engine, req, true, api::SourcePreparation::Streamed) != api::EngineStatus::Ok) {
        std::cerr << "loadLinearSource failed\n";
        api::destroyEngine(engine);
        return false;
    }
    api::setEngineOutputGain(engine, 0.0, 0.0);
    api::submitEngineCommand(engine, {api::EngineCommandKind::Play, -1, -1, 0.0, {-1}});
    // Finite loop: endFrame = 100000, fadeOutFrames = 40000 (fade across 60000..100000, spanning >1 loop wrap!).
    api::submitEngineCommand(engine, {api::EngineCommandKind::LinearFadeWindow, 0, 40000, 100000.0, {-1}});

    std::vector<float> block(20000 * 2);
    // Render 0..60000 -> full amplitude 0.5
    for (int i = 0; i < 3; ++i) {
        api::renderEngineControl(engine, block.data(), 20000);
    }
    if (std::abs(block[(19999) * 2] - 0.5f) > 1e-4f) {
        std::cerr << "Expected full amplitude before fadeStart, got " << block[19999 * 2] << "\n";
        api::destroyEngine(engine);
        return false;
    }

    // Live update BEFORE end: extend endFrame from 100000 -> 140000 (fade 100000..140000)!
    api::submitEngineCommand(engine, {api::EngineCommandKind::LinearFadeWindow, 0, 40000, 140000.0, {-1}});
    api::renderEngineControl(engine, block.data(), 20000); // 60000..80000
    if (std::abs(block[10000 * 2] - 0.5f) > 1e-4f) {
        std::cerr << "Live extension failed: frame 70000 should remain full amplitude, got " << block[10000 * 2] << "\n";
        api::destroyEngine(engine);
        return false;
    }

    // Render 80000..100000 (full), 100000..120000 (first half of fade), 120000..140000 (second half -> finishes!).
    api::renderEngineControl(engine, block.data(), 20000); // 80000..100000
    api::renderEngineControl(engine, block.data(), 20000); // 100000..120000 (midpoint ~0.25 at end)
    if (std::abs(block[19999 * 2] - 0.25f) > 5e-3f) {
        std::cerr << "Expected ~0.25 at fade midpoint, got " << block[19999 * 2] << "\n";
        api::destroyEngine(engine);
        return false;
    }
    api::renderEngineControl(engine, block.data(), 20000); // 120000..140000 -> natural end!
    api::renderEngineControl(engine, block.data(), 64); // 1 extra chunk to confirm stopped.
    const auto snap = api::engineSnapshot(engine);
    if (snap.status != 0 || snap.activeVoices != 0) {
        std::cerr << "Expected natural end at 140000, got status=" << snap.status << " voices=" << snap.activeVoices << "\n";
        api::destroyEngine(engine);
        return false;
    }

    // Test fast seek directly into fade midpoint (frame 120000) and verify 4-page warming + exact 0.25 amplitude!
    if (api::seekEngine(engine, {120000}) != api::EngineStatus::Ok) {
        std::cerr << "seekEngine into LinearFadeWindow failed\n";
        api::destroyEngine(engine);
        return false;
    }
    const auto streamSnap = api::engineStreamingSnapshot(engine);
    if (streamSnap.cachedPages < 4) {
        std::cerr << "Expected >=4 warmed pages on seek, got " << streamSnap.cachedPages << "\n";
        api::destroyEngine(engine);
        return false;
    }
    std::vector<float> seekOut(256 * 2);
    api::renderEngineControl(engine, seekOut.data(), 256);
    if (std::abs(seekOut[0] - 0.25f) > 5e-3f) {
        std::cerr << "Expected 0.25 amplitude immediately after seek to fade midpoint, got " << seekOut[0] << "\n";
        api::destroyEngine(engine);
        return false;
    }

    // Test live switch to infinite Repeat-One (endFrame = -1): must stay playing past 140000 at full 0.5!
    api::submitEngineCommand(engine, {api::EngineCommandKind::LinearFadeWindow, 0, 0, -1.0, {-1}});
    api::renderEngineControl(engine, block.data(), 20000);
    api::renderEngineControl(engine, block.data(), 20000);
    const auto repeatSnap = api::engineSnapshot(engine);
    if (repeatSnap.status != 1 || std::abs(block[19999 * 2] - 0.5f) > 1e-4f) {
        std::cerr << "Repeat-One live switch failed: status=" << repeatSnap.status << " amp=" << block[19999 * 2] << "\n";
        api::destroyEngine(engine);
        return false;
    }

    api::destroyEngine(engine);
    std::cout << "PASS: LinearFadeWindow live update, >loopLen fadeout, natural end, 4-page seek warm, Repeat-One toggle\n";
    return true;
}

bool testMultiSegmentChainAnalyticalSeek() {
    auto graph = std::make_unique<Graph>();
    graph->sampleRate = 48000;
    auto addNode = [&](int idx, Kind kind, int parent = -1) {
        Node n;
        n.index = idx;
        n.kind = kind;
        n.parent = parent;
        n.tempo.sampleRate = 48000;
        n.tempo.bpm = 120.0;
        n.properties = {0.0, 1.0, 20000.0, 20.0, 0.0, 0.0, 0.0};
        graph->nodes.push_back(n);
    };
    addNode(0, Kind::Bus);
    Bus master;
    master.index = 0;
    graph->buses.push_back(master);

    // Two assets: Stop 1 (25,000 frames @ +0.25) and Stop 2 (20,000 frames @ +0.50).
    Asset a0; a0.index = 0; a0.sampleRate = 48000; a0.channels = 2; a0.frames = 25000;
    Asset a1; a1.index = 1; a1.sampleRate = 48000; a1.channels = 2; a1.frames = 20000;
    graph->assets.push_back(a0);
    graph->assets.push_back(a1);

    // Segment 100 exits at 20,000 (leaving 5,000 frames of post-exit tail from Clip 500 overlapping Stop 2!).
    addNode(100, Kind::Segment);
    addNode(101, Kind::Segment);
    Segment s0; s0.index = 100; s0.entry = 0; s0.exit = 20000;
    Segment s1; s1.index = 101; s1.entry = 0; s1.exit = 20000;
    graph->segments.push_back(s0);
    graph->segments.push_back(s1);

    addNode(300, Kind::Track, 100);
    addNode(301, Kind::Track, 101);
    Track t0; t0.index = 300; t0.segment = 100; t0.bus = 0;
    Clip c0; c0.index = 500; c0.asset = 0; c0.start = 0; c0.sourceOffset = 0; c0.sourceLength = 25000;
    t0.clips.push_back(c0);
    Track t1; t1.index = 301; t1.segment = 101; t1.bus = 0;
    Clip c1; c1.index = 501; c1.asset = 1; c1.start = 0; c1.sourceOffset = 0; c1.sourceLength = 20000;
    t1.clips.push_back(c1);
    graph->tracks.push_back(t0);
    graph->tracks.push_back(t1);

    Transition tr;
    tr.index = 2000;
    tr.sources = {100};
    tr.destinations = {101};
    tr.sync = SyncPoint::ExitCue;
    tr.entry = EntryMode::EntryCue;
    tr.postExit = true;
    tr.timeline = TransitionTimeline{0, 0, 0, 0};
    graph->transitions.push_back(tr);
    graph->indexNodes();

    EngineRuntime runtime(std::move(graph));
    auto makeBuf = [](int idx, std::int64_t frames, float val) {
        auto b = std::make_unique<AudioBuffer>();
        b->index = idx;
        b->sampleRate = 48000;
        b->channels = 2;
        b->samples.assign(static_cast<std::size_t>(frames) * 2, val);
        return b;
    };
    if (runtime.bindAsset(makeBuf(0, 25000, 0.25f)) != api::EngineStatus::Ok) return false;
    if (runtime.bindAsset(makeBuf(1, 20000, 0.50f)) != api::EngineStatus::Ok) return false;

    runtime.submit({api::EngineCommandKind::OutputGain, 0, 0, 0.0, {-1}});
    runtime.submit({api::EngineCommandKind::Play, 100, -1, 0.0, {-1}});
    runtime.submit({api::EngineCommandKind::Play, 101, -1, 0.0, {-1}});

    std::vector<float> out(512 * 2);
    runtime.renderControl(out.data(), 512);
    if (std::abs(out[0] - 0.25f) > 1e-4f) {
        std::cerr << "Chain Stop 1 render mismatch: got " << out[0] << "\n";
        return false;
    }

    // Analytical seek straight into Stop 2 overlap seam (frame 22,000 = 2,000 frames into Stop 2;
    // Stop 1's post-exit tail is still active until 25,000 -> must sum 0.25 + 0.50 = 0.75!).
    if (runtime.seek({22000}) != api::EngineStatus::Ok) {
        std::cerr << "Analytical seek into Stop 2 seam failed\n";
        return false;
    }
    const auto seamSnap = runtime.snapshot();
    if (seamSnap.playingSegment != 101) {
        std::cerr << "Expected playingSegment=101 at frame 22000, got " << seamSnap.playingSegment << "\n";
        return false;
    }
    runtime.renderControl(out.data(), 512);
    if (std::abs(out[0] - 0.75f) > 1e-4f) {
        std::cerr << "Stop 2 seam post-exit tail mismatch: got " << out[0] << ", expected 0.75\n";
        return false;
    }

    // Analytical seek past Stop 1's tail (frame 28,000 -> Stop 2 only = 0.50!).
    if (runtime.seek({28000}) != api::EngineStatus::Ok) return false;
    runtime.renderControl(out.data(), 512);
    if (runtime.snapshot().playingSegment != 101 || std::abs(out[0] - 0.50f) > 1e-4f) {
        std::cerr << "Stop 2 steady-state seek mismatch: got " << out[0] << "\n";
        return false;
    }

    // Analytical seek back into Stop 1 (frame 5,000 -> Stop 1 only = 0.25!).
    if (runtime.seek({5000}) != api::EngineStatus::Ok) return false;
    runtime.renderControl(out.data(), 512);
    if (runtime.snapshot().playingSegment != 100 || std::abs(out[0] - 0.25f) > 1e-4f) {
        std::cerr << "Seek back to Stop 1 mismatch: got " << out[0] << "\n";
        return false;
    }

    std::cout << "PASS: Multi-segment CourseMedleyChain analytical seek (Stop 2 seam + post-exit tail + Stop 1 return)\n";
    return true;
}

std::vector<std::uint8_t> buildCodec2OpusBwav(int sampleRate, std::size_t totalFrames) {
    constexpr std::size_t kPacketFrames = 960;
    constexpr std::size_t kPreSkip = 120;
    const std::size_t packetCount = (totalFrames + kPacketFrames - 1) / kPacketFrames;
    constexpr double kPi = 3.14159265358979323846;

    // Encode a small set of real 960-sample mono CELT Opus packets (periodic 440 Hz tone)
    // and tile them across the full 6-minute track so generation is fast while exercising the
    // real libopus decoder and 16,000+ packet table of BGM_Demo_Staffroll / BGM_Seq_Result_1st.
    constexpr std::size_t kCyclePackets = 10;
    int opusErr = OPUS_OK;
    OpusEncoder* enc = opus_encoder_create(sampleRate, 1, OPUS_APPLICATION_AUDIO, &opusErr);
    if (!enc || opusErr != OPUS_OK) return {};
    opus_encoder_ctl(enc, OPUS_SET_BITRATE(48000));
    std::array<std::vector<std::uint8_t>, kCyclePackets> encodedCycle;
    std::vector<float> tone(kPacketFrames);
    std::vector<std::uint8_t> pktBuf(512);
    for (std::size_t p = 0; p < kCyclePackets + 4; ++p) {
        for (std::size_t i = 0; i < kPacketFrames; ++i) {
            const std::size_t sampleIdx = p * kPacketFrames + i;
            tone[i] = static_cast<float>(0.35 * std::sin(2.0 * kPi * 440.0 * static_cast<double>(sampleIdx) / sampleRate));
        }
        const int n = opus_encode_float(enc, tone.data(), static_cast<int>(kPacketFrames), pktBuf.data(), static_cast<opus_int32>(pktBuf.size()));
        if (n <= 0) { opus_encoder_destroy(enc); return {}; }
        if (p >= 4) {
            encodedCycle[p - 4].assign(pktBuf.begin(), pktBuf.begin() + n);
        }
    }
    opus_encoder_destroy(enc);

    std::vector<std::uint8_t> channelPayload;
    std::size_t payloadBytes = 0;
    for (std::size_t p = 0; p < packetCount; ++p) {
        payloadBytes += 8 + encodedCycle[p % kCyclePackets].size();
    }
    constexpr std::size_t kConfigBodyBytes = 28;
    const std::size_t regionBytes = 8 + kConfigBodyBytes + 8 + payloadBytes;
    channelPayload.resize(regionBytes, 0);
    // Config chunk (0x80000001)
    putLe(channelPayload, 0, 0x80000001u, 4);
    putLe(channelPayload, 4, static_cast<std::uint32_t>(kConfigBodyBytes), 4);
    putLe(channelPayload, 8 + 8, static_cast<std::uint32_t>(sampleRate), 4);
    putLe(channelPayload, 8 + 20, static_cast<std::uint32_t>(kPreSkip), 4);
    // Payload chunk (0x80000004)
    const std::size_t payOff = 8 + kConfigBodyBytes;
    putLe(channelPayload, payOff, 0x80000004u, 4);
    putLe(channelPayload, payOff + 4, static_cast<std::uint32_t>(payloadBytes), 4);
    std::size_t at = payOff + 8;
    for (std::size_t p = 0; p < packetCount; ++p) {
        const auto& pkt = encodedCycle[p % kCyclePackets];
        const auto sz = static_cast<std::uint32_t>(pkt.size());
        // Big-endian packet size + 4 opaque bytes + packet payload
        channelPayload[at + 0] = static_cast<std::uint8_t>((sz >> 24) & 0xffu);
        channelPayload[at + 1] = static_cast<std::uint8_t>((sz >> 16) & 0xffu);
        channelPayload[at + 2] = static_cast<std::uint8_t>((sz >> 8) & 0xffu);
        channelPayload[at + 3] = static_cast<std::uint8_t>(sz & 0xffu);
        std::copy(pkt.begin(), pkt.end(), channelPayload.begin() + static_cast<std::ptrdiff_t>(at + 8));
        at += 8 + pkt.size();
    }

    constexpr std::size_t channels = 2;
    const std::size_t headerSize = 16 + channels * 76;
    const std::size_t ch0Start = (headerSize + 63u) & ~std::size_t{63u};
    const std::size_t chStride = (regionBytes + 63u) & ~std::size_t{63u};
    const std::size_t totalSize = ch0Start + chStride + regionBytes;

    std::vector<std::uint8_t> out(totalSize, 0);
    out[0] = 'B'; out[1] = 'W'; out[2] = 'A'; out[3] = 'V';
    putLe(out, 4, 0xfeffu, 2);
    putLe(out, 6, 1u, 2);
    putLe(out, 12, 0u, 2);
    putLe(out, 14, static_cast<std::uint32_t>(channels), 2);
    for (std::size_t ch = 0; ch < channels; ++ch) {
        const std::size_t h = 16 + ch * 76;
        const std::size_t start = ch0Start + ch * chStride;
        putLe(out, h + 0, 2u, 2); // codec 2 = chunked Opus
        putLe(out, h + 2, static_cast<std::uint32_t>(ch), 2);
        putLe(out, h + 4, static_cast<std::uint32_t>(sampleRate), 4);
        putLe(out, h + 8, static_cast<std::uint32_t>(totalFrames), 4);
        putLe(out, h + 12, static_cast<std::uint32_t>(totalFrames), 4);
        putLe(out, h + 48, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 52, static_cast<std::uint32_t>(start), 4);
        putLe(out, h + 56, 1u, 4);
        putLe(out, h + 60, 0xffffffffu, 4);
        putLe(out, h + 64, 0u, 4);
        std::copy(channelPayload.begin(), channelPayload.end(), out.begin() + static_cast<std::ptrdiff_t>(start));
    }
    return out;
}

bool testLongTrackBeyondTenMinutesSeek() {
    constexpr int rate = 48000;
    constexpr std::size_t baseFrames = 48000; // 1s looping base
    std::vector<std::vector<std::int16_t>> ch(2, std::vector<std::int16_t>(baseFrames, 16384)); // +0.5
    auto bwav = buildPcm16Bwav(rate, {0, 1}, ch, 4000, baseFrames);

    auto* engine = api::createEngine();
    if (!engine) return false;
    api::SourceOpenRequest req;
    req.providerId = "native.bwav";
    req.assetId = "long-loop-15m";
    req.bytes = {bwav.data(), bwav.size()};
    if (api::loadLinearSource(engine, req, true, api::SourcePreparation::Streamed) != api::EngineStatus::Ok) {
        std::cerr << "loadLinearSource failed in 15m seek test\n";
        api::destroyEngine(engine);
        return false;
    }
    api::setEngineOutputGain(engine, 0.0, 0.0);
    api::submitEngineCommand(engine, {api::EngineCommandKind::Play, -1, -1, 0.0, {-1}});
    // 15-minute finite fade window (43,200,000 frames > legacy 10-minute 28,800,000 cap!).
    constexpr std::int64_t end15m = 48000LL * 60 * 15;
    constexpr std::int32_t fade10s = 48000 * 10;
    api::submitEngineCommand(engine, {api::EngineCommandKind::LinearFadeWindow, 0, fade10s, static_cast<double>(end15m), {-1}});

    std::vector<float> buf(4096 * 2);
    api::renderEngineControl(engine, buf.data(), 512);

    // Seek to 12:00 (34,560,000 > 28,800,000!), 14:55 (midpoint of 10s fade), 10:30, and 0:05.
    const std::array<std::pair<std::int64_t, float>, 4> checks{{
        {48000LL * 60 * 12, 0.50f},
        {end15m - fade10s / 2, 0.25f},
        {48000LL * 60 * 10 + 48000LL * 30, 0.50f},
        {48000LL * 5, 0.50f},
    }};
    for (const auto& [target, expectedAmp] : checks) {
        const auto status = api::seekEngine(engine, {target});
        if (status != api::EngineStatus::Ok) {
            std::cerr << "seekEngine past 10m failed at frame " << target << " status=" << static_cast<int>(status) << "\n";
            api::destroyEngine(engine);
            return false;
        }
        const auto rt = api::renderEngineRealtimeHarness(engine, buf.data(), 2048);
        if (rt != api::EngineStatus::Ok) {
            std::cerr << "realtime render failed after 15m seek at " << target << "\n";
            api::destroyEngine(engine);
            return false;
        }
        const auto streamSnap = api::engineStreamingSnapshot(engine);
        if (streamSnap.underrunFrames != 0) {
            std::cerr << "Unexpected underrun after seek at " << target << ": " << streamSnap.underrunFrames << "\n";
            api::destroyEngine(engine);
            return false;
        }
        if (std::abs(buf[0] - expectedAmp) > 5e-3f) {
            std::cerr << "Amplitude mismatch after seek at " << target << ": got " << buf[0] << " expected " << expectedAmp << "\n";
            api::destroyEngine(engine);
            return false;
        }
    }

    api::destroyEngine(engine);
    std::cout << "PASS: Long linear track seek past 10m cap (12:00, 14:55 fade midpoint, 10:30, 0:05) with 0 underruns\n";
    return true;
}

bool testLongMultiOpusBwavSeek() {
    constexpr int rate = 48000;
    // 5m23s (15,498,140 frames) mirrors BGM_Demo_Staffroll exactly!
    constexpr std::size_t staffrollFrames = 15498140;
    auto opusBwav = buildCodec2OpusBwav(rate, staffrollFrames);
    if (opusBwav.empty()) {
        std::cerr << "Failed to build synthetic Codec-2 Opus BWAV\n";
        return false;
    }

    auto* engine = api::createEngine();
    if (!engine) return false;
    api::SourceOpenRequest req;
    req.providerId = "native.bwav";
    req.assetId = "staffroll-opus-test";
    req.bytes = {opusBwav.data(), opusBwav.size()};
    if (api::loadLinearSource(engine, req, false, api::SourcePreparation::Streamed) != api::EngineStatus::Ok) {
        std::cerr << "loadLinearSource failed on 5m23s Codec-2 Opus BWAV\n";
        api::destroyEngine(engine);
        return false;
    }
    api::setEngineOutputGain(engine, 0.0, 0.0);
    api::submitEngineCommand(engine, {api::EngineCommandKind::Play, -1, -1, 0.0, {-1}});

    std::vector<float> buf(4096 * 2);
    api::renderEngineControl(engine, buf.data(), 2048);

    // Rapid forward/backward seeks across the full 5m23s track (mirroring dragging progress bar).
    const std::array<std::int64_t, 6> targets{
        7500000, 14900000, 15450000, 3200000, 12000000, 0
    };
    for (const auto target : targets) {
        const auto status = api::seekEngine(engine, {target});
        if (status != api::EngineStatus::Ok) {
            std::cerr << "Opus BWAV seek failed at " << target << " status=" << static_cast<int>(status) << "\n";
            api::destroyEngine(engine);
            return false;
        }
        const auto rt = api::renderEngineRealtimeHarness(engine, buf.data(), 4096);
        if (rt != api::EngineStatus::Ok) {
            std::cerr << "Opus BWAV realtime render failed after seek at " << target << "\n";
            api::destroyEngine(engine);
            return false;
        }
        const double rmsL = computeRms(buf, 0);
        const double rmsR = computeRms(buf, 1);
        const auto streamSnap = api::engineStreamingSnapshot(engine);
        if (rmsL < 0.05 || rmsR < 0.05 || streamSnap.underrunFrames != 0) {
            std::cerr << "Opus BWAV silent/underrun after seek at " << target
                      << " rmsL=" << rmsL << " rmsR=" << rmsR << " underruns=" << streamSnap.underrunFrames << "\n";
            api::destroyEngine(engine);
            return false;
        }
    }

    api::destroyEngine(engine);
    std::cout << "PASS: 5m23s Codec-2 Opus BWAV (BGM_Demo_Staffroll scale) random forward/backward seeks audible with 0 underruns\n";
    return true;
}

bool testSixStopSurvivalRallyLongChainSeekAndTransition() {
    // 6-stop Survival Rally chain with 6 streamed BWAV assets totaling 36,000,000 frames (12.5m > 10m cap!).
    constexpr int rate = 48000;
    constexpr std::int64_t stopExit = 6000000;   // 125s per stop -> 6 stops = 12.5 minutes!
    constexpr std::int64_t assetLen = 6004096;   // +4096 post-exit tail
    constexpr int kStops = 6;

    auto graph = std::make_unique<Graph>();
    graph->sampleRate = rate;
    auto addNode = [&](int idx, Kind kind, int parent = -1) {
        Node n;
        n.index = idx;
        n.kind = kind;
        n.parent = parent;
        n.tempo.sampleRate = rate;
        n.tempo.bpm = 120.0;
        n.properties = {0.0, 1.0, 20000.0, 20.0, 0.0, 0.0, 0.0};
        graph->nodes.push_back(n);
    };
    addNode(0, Kind::Bus);
    Bus master; master.index = 0; graph->buses.push_back(master);

    for (int i = 0; i < kStops; ++i) {
        Asset a; a.index = i; a.sampleRate = rate; a.channels = 2; a.frames = assetLen;
        graph->assets.push_back(a);
        addNode(100 + i, Kind::Segment);
        Segment s; s.index = 100 + i; s.entry = 0; s.exit = stopExit;
        graph->segments.push_back(s);
        addNode(300 + i, Kind::Track, 100 + i);
        Track t; t.index = 300 + i; t.segment = 100 + i; t.bus = 0;
        Clip c; c.index = 500 + i; c.asset = i; c.start = 0; c.sourceOffset = 0; c.sourceLength = assetLen;
        t.clips.push_back(c);
        graph->tracks.push_back(t);
        if (i + 1 < kStops) {
            Transition tr;
            tr.index = 2000 + i;
            tr.sources = {100 + i};
            tr.destinations = {100 + i + 1};
            tr.sync = SyncPoint::ExitCue;
            tr.entry = EntryMode::EntryCue;
            tr.postExit = true;
            tr.timeline = TransitionTimeline{0, 0, 0, 0};
            graph->transitions.push_back(tr);
        }
    }
    graph->indexNodes();

    EngineRuntime runtime(std::move(graph));
    // Bind 6 streamed constant-level synthetic AudioFrameSources (distinct amplitude per stop).
    class ConstStreamSource final : public audio::AudioFrameSource {
        audio::SourceMetadata meta_;
        float amp_;
        audio::SourceSamplePosition pos_{};
    public:
        ConstStreamSource(std::int64_t frames, float amp)
            : meta_{"synthetic.const", audio::SourceKind::GameStream, rate, 2, {frames},
                    audio::SeekSupport::Fast, audio::KnownDuration | audio::DeterministicRender | audio::Streaming,
                    std::nullopt, audio::SampleEncoding::IeeeFloat, 32},
              amp_(amp) {}
        const audio::SourceMetadata& metadata() const noexcept override { return meta_; }
        audio::SourceSamplePosition position() const noexcept override { return pos_; }
        audio::SourceStatus renderStatus() const noexcept override { return audio::SourceStatus::Ok; }
        std::size_t render(float* output, std::size_t frames) noexcept override {
            const auto rem = static_cast<std::size_t>(meta_.duration.value - pos_.value);
            const auto n = std::min(frames, rem);
            std::fill_n(output, n * 2, amp_);
            std::fill_n(output + n * 2, (frames - n) * 2, 0.0f);
            pos_.value += static_cast<std::int64_t>(n);
            return n;
        }
        audio::SourceStatus seek(audio::SourceSamplePosition target) noexcept override {
            if (target.value < 0 || target.value > meta_.duration.value) return audio::SourceStatus::InvalidRequest;
            pos_ = target;
            return audio::SourceStatus::Ok;
        }
        void reset() noexcept override { pos_.value = 0; }
    };

    for (int i = 0; i < kStops; ++i) {
        const float amp = 0.10f * static_cast<float>(i + 1);
        const auto st = runtime.bindStreamedSource(i, std::make_unique<ConstStreamSource>(assetLen, amp), 1024, false, false);
        if (st != api::EngineStatus::Ok) {
            std::cerr << "bindStreamedSource failed for stop " << i << " status=" << static_cast<int>(st) << "\n";
            return false;
        }
    }

    runtime.submit({api::EngineCommandKind::OutputGain, 0, 0, 0.0, {-1}});
    runtime.submit({api::EngineCommandKind::Play, 100, -1, 0.0, {-1}});
    runtime.submit({api::EngineCommandKind::Play, 101, -1, 0.0, {-1}});
    std::vector<float> out(4096 * 2);
    runtime.renderControl(out.data(), 256);

    // 1. Seek near end of Stop 1, queue 101, and cross Stop 1 -> Stop 2 boundary (records PreparationHistory!).
    if (runtime.seek({stopExit - 128}) != api::EngineStatus::Ok) return false;
    runtime.submit({api::EngineCommandKind::Play, 101, -1, 0.0, {-1}});
    runtime.renderControl(out.data(), 512);
    if (runtime.snapshot().playingSegment != 101) {
        std::cerr << "Failed to transition Stop 1 -> Stop 2\n";
        return false;
    }

    // 2. Seek BACKWARD before the transition boundary and cross Stop 1 -> Stop 2 a SECOND time.
    // Without preparationHistory_->branch(), this corrupted preparationHistory_ and broke all future seeks!
    if (runtime.seek({stopExit - 256}) != api::EngineStatus::Ok) return false;
    runtime.submit({api::EngineCommandKind::Play, 101, -1, 0.0, {-1}});
    runtime.renderControl(out.data(), 512);
    if (runtime.snapshot().playingSegment != 101 || !runtime.streamingSnapshot().preparationHistoryComplete) {
        std::cerr << "PreparationHistory lost completeness on backward seek re-transition!\n";
        return false;
    }

    // 3. Seek into Stop 6 past the 10-minute mark (frame 32,000,000 = 11m06s > 28,800,000!) and verify Stop 6 audio (0.60).
    if (runtime.seek({32000000}) != api::EngineStatus::Ok) {
        std::cerr << "Analytical seek into Stop 6 (>10m) failed!\n";
        return false;
    }
    runtime.render(out.data(), 2048); // Realtime-style render using warmed pages only!
    if (runtime.snapshot().playingSegment != 105 || std::abs(out[0] - 0.60f) > 1e-3f ||
        runtime.streamingSnapshot().underrunFrames != 0) {
        std::cerr << "Stop 6 (>10m) seek mismatch: seg=" << runtime.snapshot().playingSegment
                  << " out=" << out[0] << " underruns=" << runtime.streamingSnapshot().underrunFrames << "\n";
        return false;
    }

    // 4. Seek back to Stop 2 (frame 8,000,000) and verify Stop 2 audio (0.20) with 0 underruns.
    if (runtime.seek({8000000}) != api::EngineStatus::Ok) return false;
    runtime.render(out.data(), 2048);
    if (runtime.snapshot().playingSegment != 101 || std::abs(out[0] - 0.20f) > 1e-3f ||
        runtime.streamingSnapshot().underrunFrames != 0) {
        std::cerr << "Stop 2 return seek mismatch: seg=" << runtime.snapshot().playingSegment
                  << " out=" << out[0] << " underruns=" << runtime.streamingSnapshot().underrunFrames << "\n";
        return false;
    }

    std::cout << "PASS: 6-stop Survival Rally (12.5m) streamed chain seek >10m + backward re-transition + 0 underruns\n";
    return true;
}
}

int main() {
    if (!testSurroundDownmix()) return 1;
    if (!testLazyBwavDspAndSeek()) return 1;
    if (!testLinearFadeWindowAndSeekWarm()) return 1;
    if (!testMultiSegmentChainAnalyticalSeek()) return 1;
    if (!testLongTrackBeyondTenMinutesSeek()) return 1;
    if (!testLongMultiOpusBwavSeek()) return 1;
    if (!testSixStopSurvivalRallyLongChainSeekAndTransition()) return 1;
    std::cout << "ALL NATIVE TESTS PASSED\n";
    return 0;
}
