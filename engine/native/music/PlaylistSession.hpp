#pragma once
#include "compiled/Graph.hpp"
#include "music/Selection.hpp"
#include <bitset>

namespace player2 {
// Bounded traversal of compiled playlist groups. Item IDs, selection histories and
// cursors are graph identities; container position never creates another clock.
class PlaylistSession {
public:
    struct SwitchBranch {int level{-1},root{-1},target{-1};SwitchChoice choice;std::bitset<4096> owners;bool changed{},limited{};};
private:
    struct Level { const Playlist* playlist{}; const PlaylistItem* item{}; std::uint64_t visits{}; std::bitset<4096> branches;int branchTarget{-1};SwitchChoice branchChoice{}; };
    std::array<Level,33> stack_{};
    std::array<Level,33> lastStack_{};
    std::size_t depth_{};
    std::size_t lastDepth_{};
    bool exhausted_{true},limited_{},waitingSwitch_{};
    int group_{-1},item_{-1},last_{-1};
    bool push(const Playlist* playlist,const PlaylistItem* item) noexcept {
        if(!playlist || !item || depth_==stack_.size()) { limited_=true; return false; }
        stack_[depth_++]={playlist,item,0,{}}; return true;
    }
    template<class States> bool jumpPath(const Playlist& playlist,const PlaylistItem* item,int target,States& states,std::int64_t at,std::uint64_t epoch) noexcept {
        const auto before=depth_;
        if(!push(&playlist,item)) return false;
        if(item->index==target) return true;
        for(std::size_t n=0;n<item->children.size();++n) if(jumpPath(playlist,find(playlist.items,item->children[n]),target,states,at,epoch)) {
            const bool sequential=item->mode==PlaylistMode::SequenceContinuous;
            stack_[before].visits=sequential ? n+1 : 1;
            auto& state=states[item->index];state.jumpTo(static_cast<int>(n),at,epoch);state.bag[n]=true;
            return true;
        }
        depth_=before; return false;
    }
public:
    void clear() noexcept { depth_=lastDepth_=0; exhausted_=true; limited_=waitingSwitch_=false; group_=item_=last_=-1; }
    bool begin(const Playlist& playlist) noexcept {
        clear(); exhausted_=false; return push(&playlist,find(playlist.items,playlist.root));
    }
    bool active() const noexcept { return !exhausted_; }
    bool waitingSwitch() const noexcept {return waitingSwitch_;}
    bool limited() const noexcept { return limited_; }
    int group() const noexcept { return group_; }
    int item() const noexcept { return item_; }
    int lastSegment() const noexcept { return last_; }
    bool containsGroup(int index) const noexcept {
        if(!active()) return false;
        for(std::size_t n=0;n<depth_;++n) if(stack_[n].item->index==index && stack_[n].item->target==-1) return true;
        return false;
    }
    void activeGroups(std::bitset<4096>& groups) const noexcept {
        if(!active())return;
        for(std::size_t n=0;n<depth_;++n)if(stack_[n].item->target==-1 && stack_[n].item->index>=0 && stack_[n].item->index<4096)groups.set(stack_[n].item->index);
    }
    void musicOwners(std::bitset<4096>& owners) const noexcept {
        for(std::size_t n=0;n<depth_;++n) { owners.set(stack_[n].playlist->index); owners|=stack_[n].branches; }
    }
    SwitchBranch probeSwitch(const Graph& graph,const std::array<int,4096>& previous,const std::array<int,4096>& values) const noexcept {
        SwitchBranch current;
        if(!active())return current;
        for(std::size_t n=0;n<depth_;++n) {
            const auto& level=stack_[n];if(!find(graph.switches,level.item->target))continue;
            SwitchBranch probe;probe.level=static_cast<int>(n);probe.root=level.item->target;int target=probe.root;bool restart=false;
            for(std::size_t hops=0;hops<stack_.size();++hops) {
                const auto* branch=find(graph.switches,target);
                if(!branch){probe.target=target;break;}
                probe.owners.set(target);bool changed=false;for(int group:branch->groups)changed|=previous[group]!=values[group];
                restart|=changed && !branch->continueSame;
                const auto choice=resolveSwitch(*branch,values);if(hops==0)probe.choice=choice;target=choice.target;
                if(hops+1==stack_.size()){probe.limited=true;return probe;}
            }
            probe.changed=probe.target!=level.branchTarget || restart;
            if(probe.changed)return probe;
            current=probe;
        }
        return current;
    }
    void acceptSwitch(const SwitchBranch& probe) noexcept {
        if(probe.level<0 || static_cast<std::size_t>(probe.level)>=depth_)return;
        auto& level=stack_[probe.level];level.branchTarget=probe.target;level.branchChoice=probe.choice;level.branches=probe.owners;
    }
    bool rewindSwitch(const SwitchBranch& probe) noexcept {
        if(probe.level<0 || static_cast<std::size_t>(probe.level)>=depth_)return false;
        depth_=static_cast<std::size_t>(probe.level)+1;auto& level=stack_[probe.level];if(level.visits)--level.visits;
        exhausted_=waitingSwitch_=limited_=false;return true;
    }
    bool replaceSwitchBranch(const SwitchBranch& probe,const PlaylistSession& destination,int segment) noexcept {
        if(probe.level<0 || static_cast<std::size_t>(probe.level)>=depth_ || static_cast<std::size_t>(probe.level)+1+destination.depth_>stack_.size())return false;
        depth_=static_cast<std::size_t>(probe.level)+1;acceptSwitch(probe);
        for(std::size_t n=0;n<destination.depth_;++n)stack_[depth_++]=destination.stack_[n];
        item_=destination.depth_ ? destination.item_ : stack_[probe.level].item->index;
        group_=-1;for(std::size_t n=depth_;n>0;--n)if(stack_[n-1].item->target==-1){group_=stack_[n-1].item->index;break;}
        exhausted_=limited_=false;waitingSwitch_=segment<0;
        if(segment>=0){last_=segment;lastDepth_=depth_;lastStack_=stack_;}
        return true;
    }
    bool restoreLast() noexcept {
        if(lastDepth_==0) return false;
        stack_=lastStack_; depth_=lastDepth_; exhausted_=false; limited_=waitingSwitch_=false; return true;
    }
    template<class States> bool jump(const Playlist& playlist,int item,States& states,std::int64_t at=-1,std::uint64_t epoch=0) noexcept {
        clear(); exhausted_=false;
        if(jumpPath(playlist,find(playlist.items,playlist.root),item,states,at,epoch)) return true;
        clear(); return false;
    }
    template<class States> int next(const Graph& graph,States& states,const std::array<int,4096>& values,std::int64_t at=-1,std::uint64_t epoch=0,int* workBudget=nullptr) noexcept {
        // The graph reader rejects empty/cyclic groups. This bound also protects
        // direct test/harness graphs and long chains producing Explicit Nothing.
        waitingSwitch_=false;
        for(int work=0;work<4096 && depth_;++work) {
            if(workBudget) {if(*workBudget<=0){limited_=true;return -1;}--*workBudget;}
            auto& level=stack_[depth_-1]; const auto& item=*level.item;
            if(item.target!=-1) {
                if(item.loops!=-1 && level.visits>=static_cast<std::uint64_t>(item.loops)) { --depth_; continue; }
                ++level.visits; item_=item.index;
                level.branches.reset();level.branchTarget=item.target;level.branchChoice={};
                int target=item.target;
                for(std::size_t hops=0;hops<stack_.size();++hops) {
                    if(find(graph.segments,target)) {
                        for(std::size_t n=depth_;n>0;--n) if(stack_[n-1].item->target==-1) { group_=stack_[n-1].item->index; break; }
                        level.branchTarget=target;last_=target; lastDepth_=depth_; lastStack_=stack_; return target;
                    }
                    if(const auto* nested=find(graph.playlists,target)) {
                        level.branchTarget=target;
                        if(nested->reset) for(const auto& child:nested->items)
                            states[child.index].reset(playlistItemSeed(*nested,child));
                        if(!push(nested,find(nested->items,nested->root))) return -1;
                        break;
                    }
                    if(const auto* branch=find(graph.switches,target)) {
                        level.branches.set(target);
                        const auto choice=resolveSwitch(*branch,values);if(target==item.target)level.branchChoice=choice;
                        target=choice.target;level.branchTarget=target;
                        if(target==-1) {
                            waitingSwitch_=true;
                            for(std::size_t n=depth_;n>0;--n)if(stack_[n-1].item->target==-1){group_=stack_[n-1].item->index;break;}
                            return -1;
                        }
                    } else { limited_=true; return -1; }
                    if(hops+1==stack_.size()) { limited_=true; return -1; }
                }
                continue;
            }
            const auto count=item.children.size();
            if(count==0 || count>64 || item.index<0 || item.index>=4096) { limited_=true; return -1; }
            const bool step=item.mode==PlaylistMode::SequenceStep || item.mode==PlaylistMode::RandomStep;
            const auto quota=step ? std::uint64_t(item.loops) : std::uint64_t(item.loops)*count;
            if(item.loops!=-1 && level.visits>=quota) { --depth_; continue; }
            auto& state=states[item.index];
            int selected;
            if(item.mode==PlaylistMode::SequenceContinuous || item.mode==PlaylistMode::SequenceStep) selected=state.sequence(static_cast<int>(count),at,epoch);
            else {
                std::array<double,64> weights{};
                for(std::size_t n=0;n<count;++n) {
                    const auto* child=find(level.playlist->items,item.children[n]);
                    if(!child) { limited_=true; return -1; }
                    weights[n]=child->weight;
                }
                selected=state.weighted(weights.data(),static_cast<int>(count),item.avoidRepeat,item.random,at,epoch);
            }
            if(selected<0) { limited_=true; return -1; }
            ++level.visits; group_=item.index;
            if(!push(level.playlist,find(level.playlist->items,item.children[selected]))) return -1;
        }
        if(depth_) limited_=true;
        exhausted_=depth_==0; return -1;
    }
};
}
