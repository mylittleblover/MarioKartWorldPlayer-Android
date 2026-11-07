"""Parse a PLAYER2 compiled graph (.hex fixture) to confirm the wire format.

Reads big-endian fields exactly as GraphReader does.
"""
import struct
import sys
import zlib


class R:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def need(self, n):
        if self.p + n > len(self.d):
            raise ValueError(f"need {n} at {self.p}, size {len(self.d)}")

    def u32(self):
        self.need(4)
        v = struct.unpack_from(">I", self.d, self.p)[0]
        self.p += 4
        return v

    def i32(self):
        self.need(4)
        v = struct.unpack_from(">i", self.d, self.p)[0]
        self.p += 4
        return v

    def f64(self):
        self.need(8)
        v = struct.unpack_from(">d", self.d, self.p)[0]
        self.p += 8
        return v

    def i64(self):
        self.need(8)
        v = struct.unpack_from(">q", self.d, self.p)[0]
        self.p += 8
        return v

    def u8(self):
        self.need(1)
        v = self.d[self.p]
        self.p += 1
        return v

    def boolean(self):
        v = self.u8()
        if v > 1:
            raise ValueError(f"bad bool {v}")
        return v != 0

    def string(self):
        n = self.i32()
        if n < 0:
            raise ValueError(f"bad string len {n}")
        self.need(n)
        s = self.d[self.p:self.p + n].decode("utf-8")
        self.p += n
        return s

    def nullable_string(self):
        n = self.i32()
        if n == -1:
            return None
        self.need(n)
        s = self.d[self.p:self.p + n].decode("utf-8")
        self.p += n
        return s

    def list(self, fn):
        n = self.i32()
        if n < 0:
            raise ValueError(f"bad list len {n}")
        return [fn() for _ in range(n)]


def parse(data, verbose=True):
    r = R(data)
    magic = r.u32()
    assert magic == 0x50324731, hex(magic)
    version = r.i32()
    body = r.u32()
    checksum = r.u32()
    print(f"magic=0x50324731 version={version} body={body} size-16={len(data)-16}")
    assert body == len(data) - 16, f"body {body} != {len(data)-16}"
    crc = zlib.crc32(data[16:]) & 0xFFFFFFFF
    print(f"crc computed=0x{crc:08x} stored=0x{checksum:08x} match={crc==checksum}")

    sample_rate = r.i32()
    print(f"sampleRate={sample_rate}")
    ids = r.list(r.string)
    print(f"identities({len(ids)})={ids}")
    nid = len(ids)

    def index(optional=False):
        v = r.i32()
        lo = -1 if optional else 0
        if v < lo or v >= max(nid, 1):
            raise ValueError(f"bad index {v}")
        return v

    def node():
        n = {}
        n["index"] = index()
        n["kind"] = r.i32()
        n["parent"] = index(True)
        n["bpm"] = r.f64()
        n["num"] = r.i32()
        n["den"] = r.i32()
        n["grid"] = r.f64()
        n["gridOffset"] = r.f64()
        sep = r.i32()
        assert sep == 7, sep
        n["properties"] = [r.f64() for _ in range(7)]
        if version >= 2:
            n["configured"] = r.u32()
        return n

    nodes = r.list(node)
    for n in nodes:
        print(f"  node[{n['index']}] kind={n['kind']} parent={n['parent']} bpm={n['bpm']}")

    def cue():
        c = {}
        c["index"] = index()
        c["name"] = r.string()
        c["frame"] = r.i64()
        c["kind"] = r.i32()
        c["event"] = index(True)
        return c

    def segment():
        s = {}
        s["index"] = index()
        s["entry"] = r.i64()
        s["exit"] = r.i64()
        s["hasLoop"] = r.boolean()
        if s["hasLoop"]:
            s["loopStart"] = r.i64()
            s["loopEnd"] = r.i64()
            s["loopCount"] = r.i32()
            s["retainTail"] = r.boolean()
        s["cues"] = r.list(cue)
        return s

    segments = r.list(segment)
    for s in segments:
        print(f"  segment[{s['index']}] entry={s['entry']} exit={s['exit']} "
              f"loop={s.get('hasLoop')} cues={len(s['cues'])}")

    def clip():
        c = {}
        c["index"] = index()
        c["asset"] = index()
        c["start"] = r.i64()
        c["sourceOffset"] = r.i64()
        c["sourceLength"] = r.i64()
        c["gainDb"] = r.f64()
        c["fadeIn"] = r.i64()
        c["fadeOut"] = r.i64()
        if version >= 5 and r.boolean():
            c["loopStart"] = r.i64()
            c["loopEnd"] = r.i64()
            c["loopCount"] = r.i32()
        return c

    def send():
        return {"bus": index(), "gainDb": r.f64()}

    def subtrack():
        return {
            "index": index(),
            "weight": r.f64(),
            "value": index(True),
            "clips": r.list(clip),
        }

    def track():
        t = {}
        t["index"] = index()
        t["segment"] = index()
        t["mode"] = r.i32()
        t["group"] = index(True)
        t["bus"] = index(True)
        t["seed"] = r.i64()
        t["avoidRepeat"] = r.i32()
        t["reset"] = r.boolean()
        t["clips"] = r.list(clip)
        t["subtracks"] = r.list(subtrack)
        t["sends"] = r.list(send)
        return t

    tracks = r.list(track)
    for t in tracks:
        print(f"  track[{t['index']}] segment={t['segment']} mode={t['mode']} "
              f"clips={len(t['clips'])}")

    def playlist_item():
        it = {}
        it["index"] = index()
        it["target"] = index(True)
        it["mode"] = r.i32()
        it["random"] = r.i32()
        it["avoidRepeat"] = r.i32()
        it["weight"] = r.f64()
        it["loops"] = r.i32()
        it["children"] = r.list(lambda: index())
        if version >= 6:
            it["stateSeed"] = r.i64()
        return it

    def playlist():
        p = {}
        p["index"] = index()
        p["root"] = index()
        p["seed"] = r.i64()
        p["reset"] = r.boolean()
        p["items"] = r.list(playlist_item)
        return p

    playlists = r.list(playlist)
    print(f"  playlists={len(playlists)}")

    def condition():
        return {"group": index(), "value": index()}

    def switch():
        s = {}
        s["index"] = index()
        s["groups"] = r.list(lambda: index())
        def path():
            return {
                "index": index(),
                "conditions": r.list(condition),
                "target": index(True),
                "priority": r.i32(),
            }
        s["paths"] = r.list(path)
        s["fallback"] = index(True)
        s["continueSame"] = r.boolean()
        return s

    switches = r.list(switch)
    print(f"  switches={len(switches)}")

    def group():
        return {
            "index": index(),
            "kind": r.i32(),
            "values": r.list(lambda: index()),
            "default": index(),
        }

    groups = r.list(group)
    print(f"  groups={len(groups)}")

    def parameter():
        return {
            "index": index(),
            "min": r.f64(),
            "max": r.f64(),
            "default": r.f64(),
            "attack": r.i64(),
            "release": r.i64(),
        }

    parameters = r.list(parameter)
    print(f"  parameters={len(parameters)}")

    def action():
        a = {}
        a["kind"] = r.i32()
        a["target"] = index(True)
        a["value"] = index(True)
        a["number"] = r.f64()
        a["retainTail"] = r.boolean()
        return a

    def event():
        return {"index": index(), "actions": r.list(action)}

    events = r.list(event)
    print(f"  events={len(events)}")

    def fade():
        return {"beats": r.f64(), "offset": r.f64(), "shape": r.i32()}

    def transition():
        t = {}
        t["index"] = index()
        t["sources"] = r.list(lambda: index())
        t["destinations"] = r.list(lambda: index())
        t["specificity"] = r.i32()
        t["sync"] = r.i32()
        t["sourceCue"] = r.nullable_string()
        t["entry"] = r.i32()
        t["destinationCue"] = r.nullable_string()
        t["jumpItem"] = index(True)
        t["bridge"] = index(True)
        t["fadeOut"] = fade()
        t["fadeIn"] = fade()
        t["postExit"] = r.boolean()
        t["preEntry"] = r.boolean()
        t["priority"] = r.i32()
        t["conditions"] = r.list(condition)
        return t

    transitions = r.list(transition)
    print(f"  transitions={len(transitions)}")

    def point():
        return {"x": r.f64(), "y": r.f64(), "shape": r.i32()}

    def curve():
        return {
            "index": index(),
            "parameter": index(),
            "target": index(),
            "property": r.i32(),
            "policy": r.i32(),
            "points": r.list(point),
        }

    curves = r.list(curve)
    print(f"  curves={len(curves)}")

    def stinger():
        return {
            "index": index(),
            "trigger": index(),
            "segment": index(),
            "sync": r.i32(),
            "priority": r.i32(),
            "replace": r.boolean(),
            "cooldownBeats": r.f64(),
        }

    stingers = r.list(stinger)
    print(f"  stingers={len(stingers)}")

    def effect():
        e = {}
        e["index"] = index()
        e["kind"] = r.i32()
        e["bypass"] = r.boolean()
        sep = r.i32()
        assert sep == 10, sep
        e["parameters"] = [r.f64() for _ in range(10)]
        return e

    def bus():
        b = {}
        b["index"] = index()
        b["output"] = index(True)
        b["auxiliary"] = r.boolean()
        b["effects"] = r.list(effect)
        b["sends"] = r.list(send)
        return b

    buses = r.list(bus)
    print(f"  buses={len(buses)}")

    def asset():
        a = {}
        a["index"] = index()
        a["path"] = r.string()
        a["sampleRate"] = r.i32()
        a["channels"] = r.i32()
        a["frames"] = r.i64()
        a["lookAhead"] = r.i64()
        if version >= 3:
            r.need(32)
            a["sha256"] = bytes(r.d[r.p:r.p + 32])
            r.p += 32
        if version >= 4 and r.boolean():
            a["sourceId"] = r.string()
            a["entryId"] = r.nullable_string()
            a["subsongId"] = r.nullable_string()
            a["variantId"] = r.nullable_string()
        return a

    assets = r.list(asset)
    for a in assets:
        print(f"  asset[{a['index']}] path={a['path']} rate={a['sampleRate']} "
              f"ch={a['channels']} frames={a['frames']}")

    def entry():
        return {"index": index(), "start": index(), "actions": r.list(action)}

    entries = r.list(entry)
    print(f"  entries={len(entries)}")

    def perf_step():
        return {"index": index(), "frame": r.i64(), "action": action()}

    def performance():
        p = {}
        p["index"] = index()
        p["start"] = index()
        p["steps"] = r.list(perf_step)
        p["endFrame"] = r.i64()
        p["end"] = r.i32()
        if version >= 7:
            if r.boolean():
                p["endFade"] = {"duration": r.i64(), "offset": r.i64(), "shape": r.i32()}
            p["ending"] = index(True)
        return p

    performances = r.list(performance)
    print(f"  performances={len(performances)}")

    has_ml = r.boolean()
    print(f"  hasMusicalLoop={has_ml}")
    if has_ml:
        ml = {}
        ml["intro"] = index(True)
        ml["loop"] = index()
        ml["ending"] = index(True)
        ml["repeats"] = r.i32()
        ml["retainIntroTail"] = r.boolean()
        ml["stop"] = r.i32()
        if version >= 9:
            ml["playlist"] = index()
        print(f"    intro={ml['intro']} loop={ml['loop']} ending={ml['ending']} "
              f"repeats={ml['repeats']} playlist={ml.get('playlist')}")

    has_auto = r.boolean()
    print(f"  hasAutomatic={has_auto}")
    if has_auto:
        print("    parameter", index(), r.f64(), r.f64(), r.f64(), r.boolean(), r.i64())

    if version >= 10:
        def effect_rtpc():
            return {
                "index": index(),
                "parameter": index(),
                "bus": index(),
                "effect": index(),
                "property": r.i32(),
                "policy": r.i32(),
                "points": r.list(lambda: {"x": r.f64(), "y": r.f64(), "shape": r.i32()}),
            }
        eff = r.list(effect_rtpc)
        print(f"  effectCurves={len(eff)}")

    if version >= 11:
        def album_flow():
            return {
                "from": index(),
                "to": index(),
                "mode": r.i32(),
                "duration": r.i64(),
                "bridge": index(True),
                "retainTail": r.boolean(),
            }
        flows = r.list(album_flow)
        print(f"  albumFlows={len(flows)}")

    if version >= 12:
        def seg_env():
            n = r.i32()
            rows = []
            for _ in range(n):
                seg = index()
                target = r.i32()
                pts = r.list(lambda: {"frame": r.i64(), "value": r.f64(), "shape": r.i32()})
                rows.append((seg, target, len(pts)))
            return rows
        se = r.list(seg_env)
        print(f"  segmentEnvelopes={len(se)}")

    if version >= 13:
        def clip_env():
            n = r.i32()
            rows = []
            for _ in range(n):
                clip_idx = index()
                target = r.i32()
                pts = r.list(lambda: {"frame": r.i64(), "value": r.f64(), "shape": r.i32()})
                rows.append((clip_idx, target, len(pts)))
            return rows
        ce = r.list(clip_env)
        print(f"  clipEnvelopes={len(ce)}")

    print(f"consumed {r.p} of {len(data)}; trailing={len(data)-r.p}")
    return {
        "version": version,
        "sampleRate": sample_rate,
        "identities": ids,
        "nodes": nodes,
        "segments": segments,
        "tracks": tracks,
        "assets": assets,
    }


if __name__ == "__main__":
    path = sys.argv[1]
    text = open(path, encoding="utf-8").read().strip()
    data = bytes.fromhex(text)
    print(f"file={path} bytes={len(data)}")
    parse(data)
