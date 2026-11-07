#pragma once
#include "decoder/ModuleInputReader.hpp"
#include "libopenmpt/libopenmpt.h"
#include "libopenmpt/libopenmpt_ext.h"
#include <memory>
#include <string>

namespace player2::audio {
class ModuleRenderer {
    std::unique_ptr<openmpt_module,decltype(&openmpt_module_destroy)> basic_{nullptr,openmpt_module_destroy};
    std::unique_ptr<openmpt_module_ext,decltype(&openmpt_module_ext_destroy)> extended_{nullptr,openmpt_module_ext_destroy};
public:
    ModuleRenderer()=default;
    ModuleRenderer(openmpt_module* value,decltype(&openmpt_module_destroy) destroy):basic_(value,destroy) {}
    explicit ModuleRenderer(openmpt_module_ext* value):extended_(value,openmpt_module_ext_destroy) {}
    ModuleRenderer(ModuleRenderer&&)=default;ModuleRenderer& operator=(ModuleRenderer&&)=default;
    openmpt_module* get()const noexcept {return extended_ ? openmpt_module_ext_get_module(extended_.get()) : basic_.get();}
    explicit operator bool()const noexcept {return get()!=nullptr;}
    void reset()noexcept {basic_.reset();extended_.reset();}
    bool channels(std::uint64_t mute,std::uint64_t solo)noexcept {
        if(!extended_)return false;
        openmpt_module_ext_interface_interactive functions{};
        if(!openmpt_module_ext_get_interface(extended_.get(),LIBOPENMPT_EXT_C_INTERFACE_INTERACTIVE,&functions,sizeof(functions)))return false;
        const auto count=openmpt_module_get_num_channels(get());
        for(int n=0;n<count;++n) {
            const auto bit=std::uint64_t{1}<<n;
            if(!functions.set_channel_mute_status(extended_.get(),n,(mute&bit)!=0 || (solo!=0 && (solo&bit)==0)))return false;
        }
        return true;
    }
};
inline void moduleQuietLog(const char*,void*) {}
inline ModuleRenderer createModuleRenderer(const std::vector<std::uint8_t>& bytes,EncodedSourceInput* input,const DecodeLimits& limits,std::uint32_t seed,int subsong,bool interactive=false) {
    const auto value=std::to_string(seed);
    const openmpt_module_initial_ctl controls[]={{"player2.seed",value.c_str()},{"play.at_end","stop"},{"load.skip_plugins","1"},{nullptr,nullptr}};
    int error=0;std::unique_ptr<ModuleInputReader> reader;
    if(input)reader=std::make_unique<ModuleInputReader>(*input,limits);
    ModuleRenderer result;
    if(interactive) {
        auto* extended=reader ? openmpt_module_ext_create(ModuleInputReader::callbacks(),reader.get(),moduleQuietLog,nullptr,nullptr,nullptr,&error,nullptr,controls)
            : openmpt_module_ext_create_from_memory(bytes.data(),bytes.size(),moduleQuietLog,nullptr,nullptr,nullptr,&error,nullptr,controls);
        result=ModuleRenderer(extended);
    } else {
        auto* basic=reader ? openmpt_module_create2(ModuleInputReader::callbacks(),reader.get(),moduleQuietLog,nullptr,nullptr,nullptr,&error,nullptr,controls)
            : openmpt_module_create_from_memory2(bytes.data(),bytes.size(),moduleQuietLog,nullptr,nullptr,nullptr,&error,nullptr,controls);
        result=ModuleRenderer(basic,openmpt_module_destroy);
    }
    if(reader && reader->status()!=SourceStatus::Ok)fail(reader->status());
    if(!result)fail(error==OPENMPT_ERROR_OUT_OF_MEMORY ? SourceStatus::ResourceLimit : SourceStatus::MalformedSource);
    auto* module=result.get();
    if(!openmpt_module_select_subsong(module,subsong))fail(SourceStatus::SubsongMissing);
    float warm[2]{};openmpt_module_read_interleaved_float_stereo(module,44100,0,warm);return result;
}
}
