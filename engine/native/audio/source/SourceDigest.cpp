#include "audio/source/SourceDigest.hpp"
#include <algorithm>
namespace player2::audio {
namespace {
constexpr std::array<std::uint32_t,64> Constants{
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
std::uint32_t rotate(std::uint32_t value,unsigned bits)noexcept{return (value>>bits)|(value<<(32-bits));}
void block(std::array<std::uint32_t,8>& state,const std::uint8_t* input)noexcept {
    std::array<std::uint32_t,64> words{};
    for(std::size_t n=0;n<16;++n)for(unsigned b=0;b<4;++b)words[n]=(words[n]<<8)|input[n*4+b];
    for(std::size_t n=16;n<64;++n) {
        const auto a=words[n-15],b=words[n-2];
        words[n]=words[n-16]+(rotate(a,7)^rotate(a,18)^(a>>3))+words[n-7]+(rotate(b,17)^rotate(b,19)^(b>>10));
    }
    auto a=state[0],b=state[1],c=state[2],d=state[3],e=state[4],f=state[5],g=state[6],h=state[7];
    for(std::size_t n=0;n<64;++n) {
        const auto first=h+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+Constants[n]+words[n];
        const auto second=(rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b)^(a&c)^(b&c));
        h=g;g=f;f=e;e=d+first;d=c;c=b;b=a;a=first+second;
    }
    state[0]+=a;state[1]+=b;state[2]+=c;state[3]+=d;state[4]+=e;state[5]+=f;state[6]+=g;state[7]+=h;
}
class Sha256 {
    std::array<std::uint32_t,8> state{0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    std::array<std::uint8_t,64> pending{};std::size_t used{};std::uint64_t length{};
public:
    void update(const std::uint8_t* data,std::size_t size)noexcept {
        length+=size;
        if(used) {
            const auto count=std::min(size,64-used);std::copy_n(data,count,pending.begin()+used);used+=count;data+=count;size-=count;
            if(used==64){block(state,pending.data());used=0;}
        }
        while(size>=64){block(state,data);data+=64;size-=64;}
        if(size){std::copy_n(data,size,pending.begin());used=size;}
    }
    SourceDigest finish()noexcept {
        const auto remainder=static_cast<std::size_t>(length%64);
        std::array<std::uint8_t,128> tail{};std::copy_n(pending.begin(),remainder,tail.begin());tail[remainder]=0x80;
        const auto tailSize=remainder<56 ? 64u : 128u;const auto bits=length*8;
        for(unsigned n=0;n<8;++n)tail[tailSize-1-n]=static_cast<std::uint8_t>(bits>>(n*8));
        block(state,tail.data());if(tailSize==128)block(state,tail.data()+64);
        SourceDigest output{};for(std::size_t n=0;n<8;++n)for(unsigned b=0;b<4;++b)output[n*4+b]=static_cast<std::uint8_t>(state[n]>>(24-b*8));
        return output;
    }
};
}
bool sourceSha256(ByteView bytes,SourceDigest& output) noexcept {
    if((bytes.size && !bytes.data) || bytes.size>64*1024*1024)return false;
    Sha256 hash;if(bytes.size)hash.update(bytes.data,bytes.size);output=hash.finish();
    return true;
}
SourceStatus sourceSha256(EncodedSourceInput& input,const DecodeLimits& limits,SourceDigest& output)noexcept {
    const auto length=input.byteLength();
    if(length>limits.maximumInputBytes || length>64*1024*1024)return SourceStatus::ResourceLimit;
    Sha256 hash;std::array<std::uint8_t,8192> buffer{};
    for(std::uint64_t position=0;position<length;) {
        const auto count=static_cast<std::size_t>(std::min<std::uint64_t>(buffer.size(),length-position));
        const auto result=input.read({position},buffer.data(),count);
        if(result.status!=SourceStatus::Ok)return result.status;
        if(result.bytes!=count)return SourceStatus::InputUnavailable;
        hash.update(buffer.data(),count);position+=count;
    }
    output=hash.finish();return SourceStatus::Ok;
}
}
