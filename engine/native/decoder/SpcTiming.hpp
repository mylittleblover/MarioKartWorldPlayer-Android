#pragma once
#include "decoder/SourceBytes.hpp"
#include <array>
#include <utility>

namespace player2::audio {
// SPC v0.30 / xid6: https://snesmusic.org/files/spc_file_format.txt
// Tick precision is retained through conversion to Source frames (1/64000s).
template<class Bytes> std::pair<std::uint64_t,std::uint64_t> spcTiming(Bytes& input,const DecodeLimits& limits) {
    input.need(0,0x10200);
    std::uint64_t dry=0,fade=0;const auto tagged=input.u8(35);
    if(tagged!=0x1a && tagged!=0x1b)fail(SourceStatus::MalformedSource);
    if(tagged==0x1a) {
        bool text=true;
        for(unsigned n=0;n<8;++n){const auto c=input.u8(0xa9+n);text=text && c>='0' && c<='9';}
        if(text) {
            for(unsigned n=0;n<3;++n)dry=dry*10+input.u8(0xa9+n)-'0';
            for(unsigned n=0;n<5;++n)fade=fade*10+input.u8(0xac+n)-'0';
            if(input.u8(0xd1))fail(SourceStatus::UnsupportedCodec);
        } else {
            // Canonical binary subset: disjoint from complete decimal timing,
            // unused date bytes zero and 16-bit seconds (24-bit high byte zero).
            // Refuse ambiguous/mixed text instead of inferring a default duration.
            for(unsigned n=0xa2;n<0xa9;++n)if(input.u8(n))fail(SourceStatus::UnsupportedCodec);
            if(input.u8(0xab) || input.u8(0xd0) || input.u8(0xd1)>2)fail(SourceStatus::UnsupportedCodec);
            dry=input.u16(0xa9);fade=input.u32(0xac);
        }
        dry*=64000;fade*=64;
    }
    if(input.size()>0x10200) {
        const std::size_t start=0x10200;
        input.need(start,8);
        if(!input.magic(start,"xid6"))fail(SourceStatus::UnsupportedCodec);
        const auto size=input.u32(start+4);
        if(std::uint64_t(size)+start+8!=input.size())fail(SourceStatus::MalformedSource);
        std::array<bool,256> seen{};std::array<std::uint32_t,4> lengths{};unsigned loops=0,events=0;
        for(auto p=start+8;p<input.size();) {
            if(events++>=limits.maximumEvents)fail(SourceStatus::ResourceLimit);
            input.need(p,4);const auto id=input.u8(p),type=input.u8(p+1);const auto data=input.u16(p+2);p+=4;
            if(seen[id])fail(SourceStatus::MalformedSource);
            seen[id]=true;
            const std::size_t bytes=type ? data : 0;input.need(p,bytes);
            if(id>=0x30 && id<=0x33) {
                if(type!=4 || bytes!=4)fail(SourceStatus::MalformedSource);
                lengths[id-0x30]=input.u32(p);
                const auto value=lengths[id-0x30];
                const auto magnitude=id==0x32 && (value&0x80000000u) ? std::uint64_t(0x100000000ull-value) : value;
                if(magnitude>383999999)fail(SourceStatus::ResourceLimit);
            } else if(id==0x35) {
                if(type || data>255)fail(SourceStatus::MalformedSource);
                loops=data;
            } else if(id==0x34) {
                if(type || data)fail(SourceStatus::UnsupportedCodec); // no silent channel-mask loss
            } else if(id==0x36) {
                if(type!=4 || bytes!=4)fail(SourceStatus::MalformedSource);
                if(input.u32(p)!=65536)fail(SourceStatus::UnsupportedCodec);
            } else if(id==1 || id==2 || id==3 || id==4 || id==7 || id==0x10 || id==0x13) {
                if(type!=1 || !bytes || bytes>256 || input.u8(p+bytes-1))fail(SourceStatus::MalformedSource);
            } else if(id==5) {
                if(type!=4 || bytes!=4)fail(SourceStatus::MalformedSource);
            } else if(id==6 || id==0x11 || id==0x12 || id==0x14) {
                if(type)fail(SourceStatus::MalformedSource);
                if((id==6 && data>2) || (id==0x11 && data>255) || (id==0x12 && (data>>8)>99) || (id==0x14 && data>9999))
                    fail(SourceStatus::MalformedSource);
            } else fail(SourceStatus::UnsupportedCodec);
            p+=bytes;const auto padding=(4-(bytes&3))&3;input.need(p,padding);
            for(unsigned n=0;n<padding;++n)if(input.u8(p+n))fail(SourceStatus::MalformedSource);
            p+=padding;
        }
        if(seen[0x30] || seen[0x31] || seen[0x32] || seen[0x33] || seen[0x35]) {
            // Require complete timing, including an explicit finite loop count.
            if(!seen[0x30] || !seen[0x31] || !seen[0x32] || !seen[0x33] || !seen[0x35] || (lengths[1] && !loops))
                fail(SourceStatus::UnsupportedCodec);
            const std::int64_t end=lengths[2]<=INT32_MAX ? std::int64_t(lengths[2]) : std::int64_t(lengths[2])-0x100000000ll;
            const auto total=std::int64_t(lengths[0])+std::int64_t(lengths[1])*loops+end;
            if(total<=0)fail(SourceStatus::MalformedSource);
            dry=static_cast<std::uint64_t>(total);fade=lengths[3];
        }
    }
    if(!dry)fail(SourceStatus::UnsupportedCodec);
    return {dry,fade};
}
}
