#include "api/AudioSourceApi.hpp"
#include "audio/source/SourceHandle.hpp"
#include "audio/source/SourceSequenceData.hpp"
#include "audio/source/SourceDigest.hpp"
#include "decoder/ModuleModFormat.hpp"
#include "decoder/TrackerFormat.hpp"
#include "decoder/StandaloneDspFormat.hpp"
#include "decoder/EmulatedFormat.hpp"
#include "decoder/BfstmProbe.hpp"
#include "decoder/Mp3Layout.hpp"
#include "decoder/VorbisLayout.hpp"
#include "decoder/OpusFormat.hpp"
#include "decoder/WwiseOpusFormat.hpp"
#include "decoder/ModuleXmFormat.hpp"
#include "decoder/Mo3Format.hpp"
#include "decoder/HcaProbe.hpp"
#include <cstring>
#include <memory>
#include <new>
#include <algorithm>

namespace player2::api {
using audio::SourceStatus;
namespace {
bool stableResourceId(const char* id) noexcept {
    if(!id)return false;
    std::size_t n=0;
    for(;n<128 && id[n];++n) {
        const auto c=id[n];
        if(!((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='_' || c=='.' || c==':' || c=='-'))return false;
    }
    return n!=0 && id[n]=='\0';
}
}
SourceProviderCatalog audioProviderIds() noexcept {
    static constexpr const char* ids[]{"native.wave","native.flac","native.mp3","native.vorbis","native.bfstm-pcm","native.bfstm-dsp","native.bcstm-pcm","native.bcstm-dsp",
        "native.brstm-pcm","native.brstm-dsp","native.vgm-psg","native.module.mod","native.module.xm","native.module.mo3","native.module.it","native.module.s3m","native.module.ptm","native.nintendo-dsp","native.vgz-psg","native.emulated.nsfe","native.emulated.spc","native.cri-adx","native.bwav","native.cri-awb","native.fmod-fsb5","native.aiff","native.wwise-bnk","native.fmod-fsb","native.cri-hca","native.wwise-pck","native.opus","native.cri-acb","native.wwise-opus","native.nintendo-wave","native.nintendo-swar","native.nintendo-rwar","native.xact-xwb","native.vgm-fm","native.vgz-fm","native.sony-vag"};
    constexpr auto count=sizeof(ids)/sizeof(ids[0]);static_assert(count<=MaximumAudioProviders);
    return {ids,count};
}
const char* probeAudioSource(audio::ByteView bytes) noexcept {
    if(!bytes.data || bytes.size<4) return nullptr;
    if(audio::probeWwiseOpus(bytes))return "native.wwise-opus";
    if((std::memcmp(bytes.data,"RIFF",4)==0 || std::memcmp(bytes.data,"RIFX",4)==0) && bytes.size>=12 && std::memcmp(bytes.data+8,"WAVE",4)==0) return "native.wave";
    if(audio::probeOpus(bytes))return "native.opus";
    if(audio::probeVorbis(bytes))return "native.vorbis";
    if(std::memcmp(bytes.data,"fLaC",4)==0)return "native.flac";
    if(audio::probeHca(bytes))return "native.cri-hca";
    if(std::memcmp(bytes.data,"WBND",4)==0 || std::memcmp(bytes.data,"DNBW",4)==0)return "native.xact-xwb";
    if(std::memcmp(bytes.data,"RWAR",4)==0)return "native.nintendo-rwar";
    if(std::memcmp(bytes.data,"SWAR",4)==0)return "native.nintendo-swar";
    if(std::memcmp(bytes.data,"VAGp",4)==0)return "native.sony-vag";
    if(std::memcmp(bytes.data,"BWAV",4)==0)return "native.bwav";
    if(std::memcmp(bytes.data,"RWAV",4)==0 || std::memcmp(bytes.data,"CWAV",4)==0 || std::memcmp(bytes.data,"FWAV",4)==0 || std::memcmp(bytes.data,"SWAV",4)==0)return "native.nintendo-wave";
    if(std::memcmp(bytes.data,"@UTF",4)==0)return "native.cri-acb";
    if(std::memcmp(bytes.data,"AFS2",4)==0)return "native.cri-awb";
    if(std::memcmp(bytes.data,"AKPK",4)==0)return "native.wwise-pck";
    if(std::memcmp(bytes.data,"BKHD",4)==0)return "native.wwise-bnk";
    if(std::memcmp(bytes.data,"FSB1",4)==0 || std::memcmp(bytes.data,"FSB2",4)==0 || std::memcmp(bytes.data,"FSB3",4)==0 || std::memcmp(bytes.data,"FSB4",4)==0)return "native.fmod-fsb";
    if(std::memcmp(bytes.data,"FSB5",4)==0)return "native.fmod-fsb5";
    if(bytes.size>=12 && std::memcmp(bytes.data,"FORM",4)==0 && (std::memcmp(bytes.data+8,"AIFF",4)==0 || std::memcmp(bytes.data+8,"AIFC",4)==0))return "native.aiff";
    if(std::memcmp(bytes.data,"FSTM",4)==0 || std::memcmp(bytes.data,"CSTM",4)==0 || std::memcmp(bytes.data,"RSTM",4)==0)
        return audio::probeNintendoStream({bytes.data,std::min<std::size_t>(bytes.size,4096)});
    if(bytes.data[0]==0x1f && bytes.data[1]==0x8b && bytes.data[2]==8)return audio::probeVgzFm(bytes) ? "native.vgz-fm" : "native.vgz-psg";
    if(std::memcmp(bytes.data,"Vgm ",4)==0) {
        if(bytes.size>=48)for(auto offset:{0x10,0x2c})for(int n=0;n<4;++n)if(bytes.data[offset+n])return "native.vgm-fm";
        return "native.vgm-psg";
    }
    if(bytes.size>=20 && bytes.data[0]==0x80 && bytes.data[1]==0 && bytes.data[4]>=2 && bytes.data[4]<=4)return "native.cri-adx";
    if(audio::probeMp3(bytes))return "native.mp3";
    if(std::memcmp(bytes.data,"NSFE",4)==0)return "native.emulated.nsfe";
    if(bytes.size>=8 && std::memcmp(bytes.data,"SNES-SPC",8)==0)return "native.emulated.spc";
    if(audio::probeIt(bytes))return "native.module.it";
    if(audio::probeS3m(bytes))return "native.module.s3m";
    if(audio::probePtm(bytes))return "native.module.ptm";
    if(audio::probeMo3(bytes))return "native.module.mo3";
    if(audio::probeXm(bytes))return "native.module.xm";
    if(audio::probeMod(bytes)) return "native.module.mod";
    if(audio::probeStandaloneDsp(bytes))return "native.nintendo-dsp";
    return nullptr;
}
SourceOpenResult openAudioSource(const SourceOpenRequest& request) noexcept {
    if(!request.providerId || !request.bytes.data || request.resourceCount>MaximumSourceResources ||
        (request.resourceCount && !request.resources)) return {SourceStatus::InvalidRequest,nullptr};
    if(request.assetId && !stableResourceId(request.assetId))return {SourceStatus::InvalidRequest,nullptr};
    if(request.entryId && std::strcmp(request.providerId,"native.xact-xwb")!=0 && std::strcmp(request.providerId,"native.nintendo-rwar")!=0 && std::strcmp(request.providerId,"native.nintendo-swar")!=0 && std::strcmp(request.providerId,"native.cri-awb")!=0 && std::strcmp(request.providerId,"native.fmod-fsb5")!=0 && std::strcmp(request.providerId,"native.wwise-bnk")!=0 && std::strcmp(request.providerId,"native.fmod-fsb")!=0 && std::strcmp(request.providerId,"native.wwise-pck")!=0 && std::strcmp(request.providerId,"native.cri-acb")!=0) return {SourceStatus::EntryMissing,nullptr};
    if(request.subsongId && std::strcmp(request.providerId,"native.module.mod")!=0 && std::strcmp(request.providerId,"native.module.xm")!=0 && std::strcmp(request.providerId,"native.module.mo3")!=0 && std::strcmp(request.providerId,"native.module.it")!=0 && std::strcmp(request.providerId,"native.module.s3m")!=0 && std::strcmp(request.providerId,"native.module.ptm")!=0 && std::strcmp(request.providerId,"native.emulated.nsfe")!=0 && std::strcmp(request.providerId,"native.emulated.spc")!=0) return {SourceStatus::SubsongMissing,nullptr};
    if(request.variantId) return {SourceStatus::VariantMissing,nullptr};
    std::size_t total=request.bytes.size;
    const auto budget=std::min(request.limits.maximumInputBytes,audio::MaximumEncodedSourceBytes);
    if(total>budget) return {SourceStatus::ResourceLimit,nullptr};
    std::array<const SourceResource*,MaximumSourceResources> sorted{};
    for(std::size_t n=0;n<request.resourceCount;++n) {
        const auto& resource=request.resources[n];
        if(!stableResourceId(resource.id) || !resource.bytes.data) return {SourceStatus::InvalidRequest,nullptr};
        if(resource.bytes.size>budget-total) return {SourceStatus::ResourceLimit,nullptr};
        total+=resource.bytes.size;
        sorted[n]=&resource;
    }
    std::sort(sorted.begin(),sorted.begin()+request.resourceCount,[](const auto* a,const auto* b){return std::strcmp(a->id,b->id)<0;});
    for(std::size_t n=1;n<request.resourceCount;++n)
        if(std::strcmp(sorted[n-1]->id,sorted[n]->id)==0)return {SourceStatus::InvalidRequest,nullptr};
    try {
        auto handle=std::make_unique<AudioSourceHandle>();
        if(request.expectedRootSha256) {
            if(!audio::sourceSha256(request.bytes,handle->encodedRootSha256))return {SourceStatus::ResourceLimit,nullptr};
            if(handle->encodedRootSha256!=*request.expectedRootSha256)return {SourceStatus::InputChanged,nullptr};
        }
        if(std::strcmp(request.providerId,"native.wwise-opus")==0) handle->source=audio::openWwiseOpus(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.wave")==0) handle->source=audio::openWave(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.opus")==0) handle->source=audio::openOpus(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.vorbis")==0) handle->source=audio::openVorbis(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.mp3")==0) handle->source=audio::openMp3(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.flac")==0) handle->source=audio::openFlac(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.bfstm-pcm")==0) handle->source=audio::openBfstm(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.bfstm-dsp")==0) handle->source=audio::openBfstmDsp(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.bcstm-pcm")==0) handle->source=audio::openBcstm(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.bcstm-dsp")==0) handle->source=audio::openBcstmDsp(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.brstm-pcm")==0) handle->source=audio::openBrstm(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.brstm-dsp")==0) handle->source=audio::openBrstmDsp(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.nintendo-dsp")==0) handle->source=audio::openNintendoDsp(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.vgz-psg")==0) handle->source=audio::openVgz(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.vgz-fm")==0) handle->source=audio::openVgz(request.bytes,request.limits,true);
        else if(std::strcmp(request.providerId,"native.vgm-fm")==0) handle->source=audio::openEmulated(request.bytes,request.limits,audio::EmulatedKind::VgmFm);
        else if(std::strcmp(request.providerId,"native.emulated.nsfe")==0) handle->source=audio::openEmulated(request.bytes,request.limits,audio::EmulatedKind::Nsfe,request.subsongId);
        else if(std::strcmp(request.providerId,"native.emulated.spc")==0) handle->source=audio::openEmulated(request.bytes,request.limits,audio::EmulatedKind::Spc,request.subsongId);
        else if(std::strcmp(request.providerId,"native.cri-hca")==0) handle->source=audio::openHca(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.cri-adx")==0) handle->source=audio::openAdx(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.sony-vag")==0) handle->source=audio::openVag(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.bwav")==0) handle->source=audio::openBwav(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.nintendo-wave")==0) handle->source=audio::openNintendoWave(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.xact-xwb")==0) handle->source=audio::openXwb(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.nintendo-rwar")==0) handle->source=audio::openRwar(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.nintendo-swar")==0) handle->source=audio::openSwar(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.cri-acb")==0) handle->source=audio::openAcb(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.cri-awb")==0) handle->source=audio::openAwb(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.fmod-fsb")==0) handle->source=audio::openLegacyFsb(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.fmod-fsb5")==0) handle->source=audio::openFsb5(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.wwise-pck")==0) handle->source=audio::openPck(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.wwise-bnk")==0) handle->source=audio::openBnk(request.bytes,request.limits,request.entryId);
        else if(std::strcmp(request.providerId,"native.aiff")==0) handle->source=audio::openAiff(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.vgm-psg")==0) handle->source=audio::openVgm(request.bytes,request.limits);
        else if(std::strcmp(request.providerId,"native.module.mo3")==0) handle->source=audio::openMo3(request.bytes,request.limits,request.subsongId);
        else if(std::strcmp(request.providerId,"native.module.xm")==0) handle->source=audio::openXm(request.bytes,request.limits,request.subsongId);
        else if(std::strcmp(request.providerId,"native.module.it")==0) handle->source=audio::openTracker(request.bytes,request.limits,audio::TrackerKind::It,request.subsongId);
        else if(std::strcmp(request.providerId,"native.module.s3m")==0) handle->source=audio::openTracker(request.bytes,request.limits,audio::TrackerKind::S3m,request.subsongId);
        else if(std::strcmp(request.providerId,"native.module.ptm")==0) handle->source=audio::openTracker(request.bytes,request.limits,audio::TrackerKind::Ptm,request.subsongId);
        else if(std::strcmp(request.providerId,"native.module.mod")==0) handle->source=audio::openMod(request.bytes,request.limits,request.subsongId);
        else return {SourceStatus::UnsupportedFormat,nullptr};
        if((handle->source->sequenceInspection() || handle->source->streamInspection() || handle->source->moduleFormat()) && !audio::sourceSha256(request.bytes,handle->encodedRootSha256))return {SourceStatus::ResourceLimit,nullptr};
        return {SourceStatus::Ok,handle.release()};
    } catch(const audio::SourceFailure& failure) { return {failure.status,nullptr}; }
      catch(const std::bad_alloc&) { return {SourceStatus::ResourceLimit,nullptr}; }
      catch(...) { return {SourceStatus::MalformedSource,nullptr}; }
}
SourceOpenResult openAudioSourceInput(const SourceOpenRequest& request,std::unique_ptr<audio::EncodedSourceInput> input)noexcept {
    if(!input || !request.providerId || request.bytes.data || request.bytes.size || request.resourceCount)return {SourceStatus::InvalidRequest,nullptr};
    if(request.assetId && !stableResourceId(request.assetId))return {SourceStatus::InvalidRequest,nullptr};
    if(request.entryId && std::strcmp(request.providerId,"native.xact-xwb")!=0 && std::strcmp(request.providerId,"native.nintendo-rwar")!=0 && std::strcmp(request.providerId,"native.nintendo-swar")!=0 && std::strcmp(request.providerId,"native.cri-awb")!=0 && std::strcmp(request.providerId,"native.fmod-fsb5")!=0 && std::strcmp(request.providerId,"native.wwise-bnk")!=0 && std::strcmp(request.providerId,"native.fmod-fsb")!=0 && std::strcmp(request.providerId,"native.wwise-pck")!=0 && std::strcmp(request.providerId,"native.cri-acb")!=0)return {SourceStatus::EntryMissing,nullptr};
    if(request.subsongId && std::strcmp(request.providerId,"native.module.mod")!=0 && std::strcmp(request.providerId,"native.module.xm")!=0 && std::strcmp(request.providerId,"native.module.mo3")!=0 && std::strcmp(request.providerId,"native.module.it")!=0 && std::strcmp(request.providerId,"native.module.s3m")!=0 && std::strcmp(request.providerId,"native.module.ptm")!=0 && std::strcmp(request.providerId,"native.emulated.nsfe")!=0 && std::strcmp(request.providerId,"native.emulated.spc")!=0)return {SourceStatus::SubsongMissing,nullptr};
    if(request.variantId)return {SourceStatus::VariantMissing,nullptr};
    const bool wave=std::strcmp(request.providerId,"native.wave")==0,bfstm=std::strcmp(request.providerId,"native.bfstm-pcm")==0;
    const bool standaloneDsp=std::strcmp(request.providerId,"native.nintendo-dsp")==0;
    const bool nsfe=std::strcmp(request.providerId,"native.emulated.nsfe")==0,spc=std::strcmp(request.providerId,"native.emulated.spc")==0;
    const bool wwiseOpus=std::strcmp(request.providerId,"native.wwise-opus")==0;
    const bool opus=std::strcmp(request.providerId,"native.opus")==0;
    const bool hca=std::strcmp(request.providerId,"native.cri-hca")==0;
    const bool adx=std::strcmp(request.providerId,"native.cri-adx")==0;
    const bool vag=std::strcmp(request.providerId,"native.sony-vag")==0;
    const bool bwav=std::strcmp(request.providerId,"native.bwav")==0;
    const bool rwar=std::strcmp(request.providerId,"native.nintendo-rwar")==0,xwb=std::strcmp(request.providerId,"native.xact-xwb")==0;
    const bool swar=std::strcmp(request.providerId,"native.nintendo-swar")==0;
    const bool nintendoWave=std::strcmp(request.providerId,"native.nintendo-wave")==0;
    const bool acb=std::strcmp(request.providerId,"native.cri-acb")==0;
    const bool awb=std::strcmp(request.providerId,"native.cri-awb")==0;
    const bool legacyFsb=std::strcmp(request.providerId,"native.fmod-fsb")==0;
    const bool fsb=std::strcmp(request.providerId,"native.fmod-fsb5")==0;
    const bool pck=std::strcmp(request.providerId,"native.wwise-pck")==0;
    const bool bnk=std::strcmp(request.providerId,"native.wwise-bnk")==0;
    const bool aiff=std::strcmp(request.providerId,"native.aiff")==0;
    const bool vgmFm=std::strcmp(request.providerId,"native.vgm-fm")==0,vgzFm=std::strcmp(request.providerId,"native.vgz-fm")==0;
    const bool vgz=std::strcmp(request.providerId,"native.vgz-psg")==0;
    const bool vgm=std::strcmp(request.providerId,"native.vgm-psg")==0;
    const bool dsp=std::strcmp(request.providerId,"native.bfstm-dsp")==0;
    const bool bcstm=std::strcmp(request.providerId,"native.bcstm-pcm")==0,bcstmDsp=std::strcmp(request.providerId,"native.bcstm-dsp")==0;
    const bool brstm=std::strcmp(request.providerId,"native.brstm-pcm")==0,brstmDsp=std::strcmp(request.providerId,"native.brstm-dsp")==0;
    const bool flac=std::strcmp(request.providerId,"native.flac")==0;
    const bool vorbis=std::strcmp(request.providerId,"native.vorbis")==0;
    const bool mp3=std::strcmp(request.providerId,"native.mp3")==0;
    const bool mo3=std::strcmp(request.providerId,"native.module.mo3")==0;
    const bool xm=std::strcmp(request.providerId,"native.module.xm")==0;
    const bool it=std::strcmp(request.providerId,"native.module.it")==0,s3m=std::strcmp(request.providerId,"native.module.s3m")==0;
    const bool mod=std::strcmp(request.providerId,"native.module.mod")==0,ptm=std::strcmp(request.providerId,"native.module.ptm")==0;
    if(!vag && !vgmFm && !vgzFm && !xwb && !rwar && !swar && !nintendoWave && !wwiseOpus && !acb && !opus && !pck && !hca && !legacyFsb && !bnk && !aiff && !fsb && !awb && !bwav && !adx && !nsfe && !spc && !vgz && !standaloneDsp && !wave && !vorbis && !mp3 && !flac && !bfstm && !dsp && !bcstmDsp && !bcstm && !brstm && !brstmDsp && !vgm && !mod && !xm && !mo3 && !it && !s3m && !ptm)return {SourceStatus::UnsupportedFormat,nullptr};
    try {
        auto handle=std::make_unique<AudioSourceHandle>();
        const auto status=audio::sourceSha256(*input,request.limits,handle->encodedRootSha256);
        if(status!=SourceStatus::Ok)return {status,nullptr};
        if(request.expectedRootSha256 && handle->encodedRootSha256!=*request.expectedRootSha256)return {SourceStatus::InputChanged,nullptr};
        if(wwiseOpus)handle->source=audio::openWwiseOpus(std::move(input),request.limits);
        else if(wave)handle->source=audio::openWave(std::move(input),request.limits);
        else if(opus)handle->source=audio::openOpus(std::move(input),request.limits);
        else if(vorbis)handle->source=audio::openVorbis(std::move(input),request.limits);
        else if(mp3)handle->source=audio::openMp3(std::move(input),request.limits);
        else if(flac)handle->source=audio::openFlac(std::move(input),request.limits);
        else if(bfstm)handle->source=audio::openBfstm(std::move(input),request.limits);
        else if(dsp)handle->source=audio::openBfstmDsp(std::move(input),request.limits);
        else if(bcstm)handle->source=audio::openBcstm(std::move(input),request.limits);
        else if(bcstmDsp)handle->source=audio::openBcstmDsp(std::move(input),request.limits);
        else if(brstm)handle->source=audio::openBrstm(std::move(input),request.limits);
        else if(brstmDsp)handle->source=audio::openBrstmDsp(std::move(input),request.limits);
        else if(standaloneDsp)handle->source=audio::openNintendoDsp(std::move(input),request.limits);
        else if(vgmFm)handle->source=audio::openEmulated(std::move(input),request.limits,audio::EmulatedKind::VgmFm);
        else if(vgzFm)handle->source=audio::openVgz(std::move(input),request.limits,true);
        else if(vgz)handle->source=audio::openVgz(std::move(input),request.limits);
        else if(nsfe || spc)handle->source=audio::openEmulated(std::move(input),request.limits,nsfe ? audio::EmulatedKind::Nsfe : audio::EmulatedKind::Spc,request.subsongId);
        else if(hca)handle->source=audio::openHca(std::move(input),request.limits);
        else if(adx)handle->source=audio::openAdx(std::move(input),request.limits);
        else if(vag)handle->source=audio::openVag(std::move(input),request.limits);
        else if(bwav)handle->source=audio::openBwav(std::move(input),request.limits);
        else if(nintendoWave)handle->source=audio::openNintendoWave(std::move(input),request.limits);
        else if(xwb)handle->source=audio::openXwb(std::move(input),request.limits,request.entryId);
        else if(rwar)handle->source=audio::openRwar(std::move(input),request.limits,request.entryId);
        else if(swar)handle->source=audio::openSwar(std::move(input),request.limits,request.entryId);
        else if(acb)handle->source=audio::openAcb(std::move(input),request.limits,request.entryId);
        else if(awb)handle->source=audio::openAwb(std::move(input),request.limits,request.entryId);
        else if(legacyFsb)handle->source=audio::openLegacyFsb(std::move(input),request.limits,request.entryId);
        else if(fsb)handle->source=audio::openFsb5(std::move(input),request.limits,request.entryId);
        else if(pck)handle->source=audio::openPck(std::move(input),request.limits,request.entryId);
        else if(bnk)handle->source=audio::openBnk(std::move(input),request.limits,request.entryId);
        else if(aiff)handle->source=audio::openAiff(std::move(input),request.limits);
        else if(vgm)handle->source=audio::openVgm(std::move(input),request.limits);
        else if(mo3)handle->source=audio::openMo3(std::move(input),request.limits,request.subsongId);
        else if(it || s3m || ptm)handle->source=audio::openTracker(std::move(input),request.limits,it ? audio::TrackerKind::It : s3m ? audio::TrackerKind::S3m : audio::TrackerKind::Ptm,request.subsongId);
        else if(xm)handle->source=audio::openXm(std::move(input),request.limits,request.subsongId);
        else handle->source=audio::openMod(std::move(input),request.limits,request.subsongId);
        return {SourceStatus::Ok,handle.release()};
    }catch(const audio::SourceFailure& failure){return {failure.status,nullptr};}
     catch(const std::bad_alloc&){return {SourceStatus::ResourceLimit,nullptr};}
     catch(...){return {SourceStatus::MalformedSource,nullptr};}
}
SourceEntryIndexResult inspectAudioContainerEntries(const char* provider,audio::ByteView bytes,const audio::DecodeLimits& limits)noexcept {
    if(!provider || !bytes.data)return {SourceStatus::InvalidRequest,{}};
    try {
        if(std::strcmp(provider,"native.xact-xwb")==0)return {SourceStatus::Ok,audio::inspectXwbEntries(bytes,limits)};
        if(std::strcmp(provider,"native.nintendo-rwar")==0)return {SourceStatus::Ok,audio::inspectRwarEntries(bytes,limits)};
        if(std::strcmp(provider,"native.nintendo-swar")==0)return {SourceStatus::Ok,audio::inspectSwarEntries(bytes,limits)};
        if(std::strcmp(provider,"native.cri-acb")==0)return {SourceStatus::Ok,audio::inspectAcbEntries(bytes,limits)};
        if(std::strcmp(provider,"native.cri-awb")==0)return {SourceStatus::Ok,audio::inspectAwbEntries(bytes,limits)};
        if(std::strcmp(provider,"native.wwise-pck")==0)return {SourceStatus::Ok,audio::inspectPckEntries(bytes,limits)};
        if(std::strcmp(provider,"native.wwise-bnk")==0)return {SourceStatus::Ok,audio::inspectBnkEntries(bytes,limits)};
        if(std::strcmp(provider,"native.fmod-fsb")==0)return {SourceStatus::Ok,audio::inspectLegacyFsbEntries(bytes,limits)};
        if(std::strcmp(provider,"native.fmod-fsb5")==0)return {SourceStatus::Ok,audio::inspectFsb5Entries(bytes,limits)};
        return {SourceStatus::UnsupportedFormat,{}};
    }
    catch(const audio::SourceFailure& failure){return {failure.status,{}};}
    catch(const std::bad_alloc&){return {SourceStatus::ResourceLimit,{}};}
    catch(...){return {SourceStatus::MalformedSource,{}};}
}
SourceRenderResult audioSourceEntries(const AudioSourceHandle* handle,std::size_t offset,audio::SourceEntry* output,std::size_t capacity)noexcept {
    if(!handle || !output || !capacity || capacity>128)return {SourceStatus::InvalidRequest,0};
    const auto* entries=handle->source->sourceEntries();if(!entries)return {SourceStatus::UnsupportedFormat,0};
    if(offset>entries->size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,entries->size()-offset);
    if(count)std::copy_n(entries->data()+offset,count,output);
    return {SourceStatus::Ok,count};
}
void closeAudioSource(AudioSourceHandle* handle) noexcept { delete handle; }
SourceStatus audioSourceMetadata(const AudioSourceHandle* handle,audio::SourceMetadata& metadata) noexcept {
    if(!handle) return SourceStatus::InvalidRequest;
    metadata=handle->source->metadata(); return SourceStatus::Ok;
}
SourceRenderResult audioSourceSubsongs(const AudioSourceHandle* handle,std::size_t offset,audio::SourceSubsong* output,std::size_t capacity)noexcept {
    if(!handle || !output || !capacity || capacity>128)return {SourceStatus::InvalidRequest,0};
    if(const auto* songs=handle->source->sourceSubsongs()) {
        if(offset>songs->size())return {SourceStatus::InvalidRequest,0};
        const auto count=std::min(capacity,songs->size()-offset);
        if(count)std::copy_n(songs->data()+offset,count,output);
        return {SourceStatus::Ok,count};
    }
    if(const auto* songs=handle->source->moduleSubsongs()) {
        if(offset>songs->size())return {SourceStatus::InvalidRequest,0};
        const auto count=std::min(capacity,songs->size()-offset);
        for(std::size_t n=0;n<count;++n){output[n]={};std::copy(songs->at(offset+n).id.begin(),songs->at(offset+n).id.end(),output[n].id.begin());}
        return {SourceStatus::Ok,count};
    }
    return {SourceStatus::UnsupportedFormat,0};
}
audio::SourceSamplePosition audioSourcePosition(const AudioSourceHandle* handle) noexcept {
    return handle ? handle->source->position() : audio::SourceSamplePosition{};
}
SourceRenderResult renderAudioSource(AudioSourceHandle* handle,float* output,std::size_t frames,std::size_t sampleCapacity) noexcept {
    if(!handle || !output || frames>sampleCapacity/static_cast<std::size_t>(handle->source->metadata().channels)) return {SourceStatus::InvalidRequest,0};
    const auto count=handle->source->render(output,frames);
    return {handle->source->renderStatus(),count};
}
SourceStatus seekAudioSource(AudioSourceHandle* handle,audio::SourceSamplePosition position) noexcept {
    return handle ? handle->source->seek(position) : SourceStatus::InvalidRequest;
}
SourceStatus resetAudioSource(AudioSourceHandle* handle) noexcept {
    if(!handle)return SourceStatus::InvalidRequest;
    handle->source->reset();return handle->source->renderStatus();
}
SourceStatus audioSequenceSummary(const AudioSourceHandle* handle,audio::SequenceSummary& summary) noexcept {
    summary={};if(!handle)return SourceStatus::InvalidRequest;
    const auto* data=handle->source->sequenceInspection();
    if(!data) {
        const auto* module=handle->source->moduleFormat();if(!module)return SourceStatus::UnsupportedFormat;
        summary={audio::SequenceKind::Module,handle->source->metadata().sampleRate,4,module->cells.size(),std::nullopt,handle->encodedRootSha256};return SourceStatus::Ok;
    }
    summary=data->summary;summary.encodedRootSha256=handle->encodedRootSha256;return SourceStatus::Ok;
}
SourceRenderResult audioSequenceEvents(const AudioSourceHandle* handle,std::size_t offset,audio::SequenceEvent* events,std::size_t capacity) noexcept {
    if(!handle || !events || capacity==0 || capacity>audio::MaximumSequencePage)return {SourceStatus::InvalidRequest,0};
    const auto* data=handle->source->sequenceInspection();if(!data)return {SourceStatus::UnsupportedFormat,0};
    if(offset>data->events.size())return {SourceStatus::InvalidRequest,0};
    const auto count=std::min(capacity,data->events.size()-offset);if(count)std::copy_n(data->events.data()+offset,count,events);return {SourceStatus::Ok,count};
}
}
