# MKW Soundtrack Pack (`.mkwpack`), format version 1

This document is the protocol. It is written so a second implementer can build a pack
writer or a pack reader without reading this repository's Kotlin, and so a later
maintainer cannot "simplify" a rule without seeing what it was protecting against.

Status: `formatVersion` 1. Reader and model live in
`app/src/main/kotlin/com/mylittleblover/mkwplayer/pack/`.

---

## 0. Why this format exists

A Mario Kart World stage theme is not one file. It is a set of parts the game switches
between: a short prelude, a full prelude, a bridge, the main loop, and one or more
outros. Measured on `BGM_Stage_Coaster`:

| part | frames | length | loop |
|---|---:|---:|---|
| Pre_Short | 534,857 | 11.143 s | 205714..534857 |
| Pre | 2,427,426 | 50.571 s | 452571..2427426 |
| Bridge | 88,320 | 1.840 s | none |
| Main | 3,373,711 | 70.286 s | 493714..3373711 |
| Outro00 | 226,286 | 4.714 s | none |

One stage is therefore five to eight assets. Thirty stages are roughly two hundred
files, and the jukebox catalogue adds several hundred more. Asking a user to hand-pick
those one file at a time is not a feature, it is a defect: they must pick hundreds of
files, in the right combinations, before hearing anything.

The pack replaces that with one import. One file carries every audio asset, the stage
structures, the music-graph configurations, the marker/cue data, the route data,
artwork, and checksums.

The protocol therefore has to answer a question an ordinary music format does not:
**how does a file state what it knows, what it merely chose, and what it could not
verify, in a way the runtime is not allowed to improve on?**

---

## 1. Container

### 1.1 ZIP64, and why

A `.mkwpack` is a **ZIP64** archive. Not ZIP32, not a custom stream, not a tarball.

ZIP32 stores sizes and offsets in 32-bit fields. A lossless soundtrack pack does not
fit:

- 30 course themes at 5 parts each, averaging 60 s of 48 kHz 16-bit stereo, is about
  1.7 GB (`48000 x 2 ch x 2 B = 192,000 B/s`, ~11.5 MB per minute).
- The measured catalogue's jukebox set is 227 tracks. At a conservative 3 minutes each
  that is ~7.8 GB on its own.
- Cutscenes, sequences, battle themes, artwork and the checksum index add more.

A full pack is routinely past 10 GB, so a 32-bit archive cannot even address it. ZIP64
raises both the size and offset fields to 64 bits. ZIP64 also removes the 65,535-entry
cap, which matters because a pack carries one entry per asset plus one per graph, marker
set, route and artwork record.

ZIP64 also gives something the import path needs: a central directory. The reader can
list and verify every member without decrypting or decoding anything, and can stream one
member at a time. A single concatenated stream would force a full pass before the first
asset could be checked.

### 1.2 Rules a reader must enforce before reading anything else

1. **`manifest.json` exists at the archive root, exactly that name, and SHOULD be the
   first entry** so a reader rejects an unusable pack before touching 10 GB of audio.
2. Member names are restricted. A reader MUST reject any member whose name:
   - is absolute, or contains a drive letter;
   - contains `..` as a path segment;
   - contains a backslash;
   - is not under one of the directories in section 2;
   - has more than one path separator (the layout is exactly one level deep).

   This is not theoretical: a ZIP member named `../../files/library.json` would
   otherwise be written outside the staging directory. The directory check in section 2
   is the whole defence, so it is not optional.
3. Encryption is not used. A reader that meets an encrypted entry rejects the pack.
4. Audio entries are `STORE`d (method 0): compressed audio does not compress, and a
   stored entry can be verified against its declared size without inflating it. Text
   entries (`manifest.json`, `catalog.json`, graphs, markers, routes) may be `DEFLATE`d.
   Artwork may be either. A reader must support both methods.
5. The `.mkwpack` extension is a claim, not a security boundary. A reader identifies a
   pack by `manifest.json`, never by the file extension or the MIME type the platform
   reports.

### 1.3 Media type

The file is `application/zip`. A pack on disk is named `<packId>-v<packVersion>.mkwpack`
clientside, but the name is informational: identity lives in the manifest.

---

## 2. Directory layout

```
manifest.json                   required, root, first entry
checksums.json                  required, integrity index for non-asset members
catalog.json                    required, the pack's own catalogue of its tracks
audio/<sha256>                  one entry per asset; no extension, no subdirectories
music-graphs/<trackSlug>.json   one per track with a compiled graph (optional)
markers/<stageCode>.json        cue/marker data per stage (optional)
routes/<routeId>.json           route presets (optional)
artwork/<sha256>.<ext>          images, content addressed (optional)
```

Nothing else may appear at the root. A member outside these directories is refused,
even if it is harmless, because "unknown extra files" is how a pack starts carrying
payloads nothing validates.

Member names inside `music-graphs/` are derived, not declared: the track key with `:`
replaced by `_`. For example the track key `course:1111...` (32 hex characters) becomes
`music-graphs/course_1111....json`. Deriving the name means no manifest field carries a
path, so a manifest cannot point outside the archive.

`markers/<stageCode>.json` and `routes/<routeId>.json` use the same rule: the stage code
or route id must match `[A-Za-z0-9_-]{1,64}` and is used verbatim as the file name.

### 2.1 `audio/` is content addressed

The entry name **is** the asset's SHA-256, 64 lowercase hex characters, no extension, no
subdirectory. Consequences:

- the archive cannot hold two copies of one asset under two names;
- a reader that extracts `audio/<x>` can verify it by hashing the bytes and comparing to
  `<x>`, with no lookup table involved;
- moving an asset between packs keeps its identity.

The media type and codec are **declared** by the manifest, not inferred from a file
extension, because there is none.

---

## 3. Stable identity

### 3.1 Asset id

An asset is a SHA-256 digest of its exact bytes, written as 64 lowercase hex characters,
and it is **the only way an asset is ever referenced**. Never a path, never a file name,
never an index into the asset list.

The digest is over the stored bytes, not over decoded audio. A re-encoded file is a
different asset, and the protocol says so rather than pretending otherwise: the digest
is the one claim the format can make without trusting anybody.

### 3.2 Logical track key

A logical track is one thing a user listens to, made of one or more assets. Its key is
derived, not chosen:

```
trackKey = <kind in lowercase> ":" <first 32 hex characters of the primary asset digest>
```

For example `course:5d41402abc4b2a76b9719d911017c592`. The kind is one of the catalogue
kinds (`course`, `jukebox`, `battle`, `sequence`, `cutscene`, `mission`, `demo`,
`sound_effect`, `boot`, `unclassified`).

Two consequences, both deliberate:

- The same primary asset used as a course theme and as a jukebox entry is **two**
  tracks, because the kind is part of the key. Merging them would silently lose one.
- A track key cannot be renamed. If a pack declares a key that does not match its own
  kind and primary digest, the pack is invalid (`TRACK_KEY_INCONSISTENT`). A "stable"
  key that a bulletin can rewrite is not an identity.

### 3.3 Playback configuration references ids

A compiled graph (`music-graphs/`) names assets by digest and cues by
`(trackKey, markerId)`. It never names a position in a file, a catalogue row, or a
cache file. A graph therefore describes *content*, not *location*.

### 3.4 What this buys, and what it costs

Because identity is content-derived, these operations do **not** invalidate anything a
user saved:

- **Re-issuing a pack.** A new pack version that adds artwork, routes, more outros, or a
  corrected display name leaves every unchanged asset's digest alone. A saved listening
  version that refers to those assets still resolves, and a music graph still compiles.
- **Supporting another codec.** A build that learns to decode codec 2 changes what it can
  *play*, never what an asset *is*. No id moves. A track that was unplayable becomes
  playable with the same identity.
- **Moving or renaming anything on disk.** Nothing in the format contains a path.

The one operation that does change identity is **re-encoding the audio**, because the
bytes change and the digest is honest about it. That is not an identity failure and must
not be treated as an in-place upgrade:

- a re-encoded asset gets a new digest and therefore a new track key;
- the pack that carries it must be a new pack version, and the old assets must not be
  deleted while a saved listening version still references them;
- the app must present the re-encode as an addition, never as a replacement under the
  same id.

So the rule for an installer is: **never delete an installed asset that a saved user
version references, and never rewrite an asset under an id it already has.** An import
that replaces an existing asset with different bytes under the same digest is a
corrupt pack and must be refused (the checksum comparison catches it).

---

## 4. What the pack must record, and what the runtime may not derive

The app has code that derives these facts from file names for the development import
path (`StageFlowDiscovery.prefixOf`, `StageFlowReader.roleOf`). That code exists to make
hand-picked files usable. **It must not run against pack content.** A name-derived role
is a guess, and a guess inside a pack is indistinguishable from a stated fact once the
manifest has been accepted.

The reader in this repository therefore has no `roleOf`-style helper at all: every fact
below is read from a field, and a missing field is a failure, never a fallback.

| Fact | Where the pack states it | What guessing it looks like |
|---|---|---|
| section roles (Pre_Short, Pre, Bridge, Main, Outro) | `tracks[].parts[].role` | treating `_Fast` as Main, or `_Pre_Short` as Pre because the substring matches |
| which ending is which | `tracks[].parts[].outro` | keeping whichever outro file was read first, so one ending disappears |
| cue positions and their evidence level | `markers/<stage>.json`, with `provenance` | presenting a marker offset as a transition the game schedules |
| loop window | `assets[].loopStart` / `loopEnd` | looping the whole file, which plays the intro forever |
| transition sync points | `tracks[].timing` | picking `Immediate` and calling the result "the game's transition" |
| how many main-loop passes | `tracks[].timing.mainRepeats` | defaulting to 1 or 4; both change the length of a listen |
| fade lengths | `tracks[].timing.bridgeFade{In,Out}Beats` | inventing an audible crossfade that was never measured |
| stage to track mapping | `stages[].tracks` | matching on display name, which changes with translation |

If a fact is missing, the reader **refuses the affected track**; it does not fill it in.
The format has no defaults for any row of the table above, which is why an omitted field
is a validation failure rather than a zero.

---

## 5. Provenance is a required field

Every declared timing carries a provenance record. A pack that declares a timing without
one is invalid (`TIMING_WITHOUT_PROVENANCE`), and a pack whose provenance list is empty
is invalid (`PROVENANCE_MISSING`).

Levels. These four are the complete vocabulary of v1; a fifth is a format change, not an
extension.

| Level | Means | Example |
|---|---|---|
| `MEASURED_L1` | A resource-structure fact: a field in a container, a marker record at a named position. | the 174 `Full_ToOutroNN` marker positions in the research export |
| `MEASURED_L2` | A fact established by measuring the audio itself. | a decoded waveform matching a reference render sample for sample; loop coordinates read from a BWAV header |
| `CHOSEN_BY_PROJECT` | A choice this project made because no evidence establishes the alternative. | `preludeExit = EXIT_CUE`, `mainRepeats = 2` |
| `UNVERIFIED` | Recorded, but nothing establishes it. The value is kept so the UI can show it and say so. | an outro name whose meaning has not been established |

Rules:

1. An unknown level is refused, never defaulted. A pack claiming a level this build does
   not know must fail loudly instead of being read as the weakest one: that is how an
   `L3_VERIFIED` claim would get laundered into "unverified" and then displayed as data.
2. A record names its subject and the field it covers, so a provenance claim cannot
   float free of the value it describes. For a timing the subject is the track key and
   the field is `timing`.
3. A `MEASURED_L1` marker position says nothing about when the game *uses* a transition.
   A pack must not label a cue list as a schedule. The marker file's own `provenance`
   field is `L1_MARKER_ONLY` in v1, for exactly that reason.
4. **A section timing may never be labelled measured.** `preludeExit`, `bridgeEntry`,
   `outroEntry`, `outroSwitch`, `mainRepeats` and the bridge fades are scheduling
   decisions. No container field and no audio measurement establishes when the game makes
   a transition, so a timing record whose level is `MEASURED_L1` or `MEASURED_L2` is
   invalid (`TIMING_PROVENANCE_OVERCLAIMS`). This is the one place the format refuses a
   claim rather than merely disclaiming it: a marker offset is a real L1 fact about a
   file, and reusing its authority for a timing is the exact substitution this project
   has already had to undo once.
5. Provenance travels with the data. The UI must show it wherever the value is shown; the
   runtime must never drop it, because a timing shown without its level is read as a fact.

---

## 6. Coverage and codec compatibility must be declared

The manifest states, in `coverage`:

- `totalLogicalTracks`: how many logical tracks the pack contains;
- `playableLogicalTracks`: how many of those the pack itself claims are playable;
- `codecKinds`: every codec kind used by any asset in the pack.

`playableLogicalTracks` is a statement about the pack, not about the reader: it means
"tracks whose every asset this pack lists, with a non-zero size". A reader recomputes that
from the asset list and refuses a pack whose declared number cannot be supported by its
own contents (`PackCoverage.Coverage.declaredCoverageMatches`). A pack cannot declare 650
playable tracks while listing 412. The declared `codecKinds` list is checked the same
way: it must equal the kinds its assets actually use, because an omitted kind would
otherwise hide an undecodable asset behind an honest-looking count.

What this build can *actually* play is a separate computation, done against the
reader's decoder capability:

- `PackCoverage` reports how many of the pack's tracks this build can decode;
- it lists every codec present that this build cannot decode, with the number of tracks
  affected;
- `isComplete` is false when any track is unplayable.

The wording rule is strict: **the app must never show "100 %" for a pack with
undecodable assets.** The coverage type deliberately exposes no percentage at all, so
there is nothing to round up. It exposes counts and two sentences instead.

There are two different completeness facts, and conflating them is the mistake this
section exists to prevent:

| Property | Question | False when |
|---|---|---|
| `isPackComplete` | Is the file internally consistent and whole? | a declared count does not match the content, an asset is missing from the package, a stage names a track that is not there |
| `isComplete` | Can this build play everything the file contains? | any logical track is unplayable, for any reason |

A pack can be complete and still not be fully playable, and the UI must say both: "the
pack is complete; this build can play 412 of its 650 tracks" is a true sentence. "100 %"
is not.

---

## 7. FULL and DELTA packs

```json
"kind": "FULL"    // or "DELTA"
```

A `FULL` pack is a complete pack on its own. A `DELTA` pack modifies an installed base
pack and must name it:

```json
"kind": "DELTA",
"basePackId": "org.example.mkw.base",
"basePackVersion": 4
```

Rules:

1. A `DELTA` **must** name `basePackId` and `basePackVersion`
   (`DELTA_WITHOUT_BASE`). A version number alone is not enough: version 4 of two
   different packs is not the same base. A delta without a base id would be applied to
   whichever pack happened to be installed and would produce a mixture of two products.
2. A `FULL` pack **must not** name a base (`FULL_WITH_BASE`). A base makes a pack a
   delta, and importing it as a full pack would silently drop everything the base held.
3. A delta must move forward: `packVersion > basePackVersion` (`DELTA_NOT_FORWARD`).
4. Importing a delta whose base is not installed, or is installed at a different version,
   or is a different pack id, is **refused before anything is written**. It is never
   half-applied: the staging directory is discarded and the installed pack is untouched.
5. A delta carries only the members it changes. The result of applying a delta is
   base content overlaid by delta content, keyed by digest. A delta must not remove
   content in v1: a pack that needs to remove something is re-issued as a new `FULL`.
6. A delta must not change the unlock scope of a frozen base. If the base is frozen, the
   delta's `unlock` block must be identical; otherwise it is refused, because a delta
   able to rewrite the eligible stage list could add a stage the user has already
   listened to and shorten the cup to nothing.

---

## 8. The unlock contract

The Special Cup's eligible stage list comes from **the pack's own verified, frozen
logical-track list**, never from what the user happens to have imported.

```json
"unlock": {
  "frozen": true,
  "stages": ["Coaster", "PeachBeach", "..."]
}
```

- `stages` is the frozen list of stage codes the cup requires. It must be non-empty and
  every entry must be a stage the pack describes with tracks the pack actually contains
  (`UNLOCK_SCOPE_EMPTY`, `UNLOCK_STAGE_UNKNOWN`, `UNLOCK_STAGE_INCOMPLETE`).
- `frozen` states that this list will not change for this pack version. Only a frozen
  list may define cup eligibility.
- A pack that is not `FULL`, whose scope is not frozen, whose content is incomplete, or
  whose scope contains a track this build cannot decode, yields **no eligible stages at
  all**. `PackCoverage.cupEligibleStages` returns the empty set in every one of those
  cases, and `cupBlocker` names which case it was.

Why eligibility may never come from the user's files: if it did, eligibility would be a
property of what was imported. Import one stage, listen to it for thirty seconds, and
`allCompleted` would be true over a one-element set and the cup would open. The
`set.isNotEmpty()` clause in `CupEligibility` closes the empty-set trap, but it cannot
close this one: a one-stage set is not empty. The only defence is that the set comes
from a document the user cannot edit and the import cannot shrink.

Frozen means frozen: the list is a property of the pack version. A re-issued pack that
changes it is a new version, and the app may only adopt the new list if the old one was
never satisfied. A delta may not change it at all (section 7, rule 6).

---

## 9. Import: atomicity and verification

An import must not be able to damage what it replaced.

### 9.1 Stream, never buffer

The pack is read through the archive's central directory, one entry at a time. No pass
loads the pack, or an asset, wholly into memory: audio assets are hundreds of megabytes
in aggregate and the app runs on a phone. Each asset is copied through a fixed buffer
(64 KiB) into its staging path while being hashed.

### 9.2 Verify per asset

For every audio member the importer:

1. checks the entry name is `audio/<digest>` and the digest is 64 lowercase hex;
2. checks the declared `byteSize` matches the entry's uncompressed size **before**
   reading (a discrepancy aborts without writing);
3. streams the bytes, computing SHA-256 as it goes;
4. compares the computed digest to the member name and to the manifest's `assets[].digest`;
5. checks the extracted set is exactly the manifest's asset list: no missing entry, no
   extra entry.

A member that fails any of these aborts the whole import. A partially verified pack is
not installed; it is deleted.

`checksums.json` covers every non-audio member (`manifest.json`, `catalog.json`,
graphs, markers, routes, artwork) with `sha256` and `bytes`. It cannot cover itself; its
integrity rests on the archive being intact, and the protocol makes no claim beyond
corruption detection. **There is no signature in v1**, so checksums detect damage, not
forgery.

### 9.3 Stage, then swap

```
staging/  <packId>/<packVersion>.tmp/     <- everything is written here
    audio/<sha256> ...
    manifest.json ...
```

1. Extract and verify into `<library>/.staging/<random>/`. The directory name is random
   so a previous failed attempt cannot be resumed into a half-merged state.
2. Parse and validate the manifest **first**, before extracting audio. A structurally
   invalid pack must cost nothing.
3. For a delta, resolve and verify the base pack before writing any member. If the base
   does not match `(basePackId, basePackVersion)`, abort with a refusal, having written
   nothing.
4. On success, `fsync` the staging directory, then **rename it atomically** into
   `<library>/packs/<packId>/<packVersion>/`. A rename within one filesystem is atomic:
   after a crash either the old tree or the new one exists, never a mixture.
5. Update the installed-pack pointer (`installed.json`) through a temp file and
   `Files.move(..., REPLACE_EXISTING)`, the same pattern `LibraryStore` uses. The pointer
   is the commit point: until it moves, the new tree is unreferenced and can be deleted.
6. On any failure, delete the staging directory and leave the previously installed pack
   untouched, pointer included. The error reported to the user names the failed asset or
   member. A previous pack that is still referenced by a saved listening version must be
   kept, not replaced.
7. Never extract over an installed tree, and never delete existing assets during an
   import. Garbage collection is a separate, explicit operation that must consult saved
   versions first.

---

## 10. `manifest.json` reference

All keys are case sensitive. Unknown keys are ignored; unknown *values* of a known key
are refused (section 12).

### 10.1 Top level

| Key | Type | Required | Rule |
|---|---|---|---|
| `formatVersion` | integer | yes | must be `1`; anything else is refused without reading further |
| `packId` | string | yes | `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`; it becomes a directory name, so it may not contain a separator or `..` |
| `packVersion` | integer | yes | >= 1, increases per `packId` |
| `name` | string or absent | no | human name; absent or blank falls back to `packId` |
| `kind` | `"FULL"` or `"DELTA"` | yes | anything else is refused |
| `basePackId` | string or null | for `DELTA` | required for `DELTA`, forbidden for `FULL` |
| `basePackVersion` | integer or null | for `DELTA` | required for `DELTA`, forbidden for `FULL`, >= 0 |
| `coverage` | object | yes | section 10.2 |
| `assets` | array | yes | section 10.3 |
| `tracks` | array | yes | section 10.4 |
| `stages` | array | yes (may be empty) | section 10.5 |
| `provenance` | array | yes | section 10.6 |
| `unlock` | object | yes | section 10.7 |

### 10.2 `coverage`

| Key | Type | Rule |
|---|---|---|
| `totalLogicalTracks` | integer | >= 0; must equal the number of entries in `tracks` for a complete pack |
| `playableLogicalTracks` | integer | `0 <= n <= totalLogicalTracks`; a value above the total is refused at parse time |
| `codecKinds` | array of integers | every codec kind any asset uses; must be present (may be empty only for an empty pack, which is itself invalid) |

### 10.3 `assets[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `digest` | string | yes | 64 lowercase hex; malformed values are refused |
| `byteSize` | integer | yes | >= 0 and > 0 for audio; negative and zero are validation failures |
| `mediaType` | string | yes | non-blank; e.g. `audio/x-bwav` |
| `codecKind` | integer | yes | >= 0; matches the BWAV channel codec kind, `1` = DSP-ADPCM |
| `sampleRate` | integer | yes | > 0 |
| `channels` | integer | yes | > 0 |
| `frames` | integer | yes | > 0 |
| `loopStart` | integer or null | no | present together with `loopEnd`, `0 <= start < end <= frames` |
| `loopEnd` | integer or null | no | absent means the asset does not loop; it is never 0 |

An absent loop is `null`, never `0`. Frame 0 is a real position; a loop of `0..0` would
be a claim that the asset loops over nothing.

### 10.4 `tracks[]`

| Key | Type | Required | Rule |
|---|---|---|---|
| `key` | string | yes | must equal section 3.2's derivation, else `TRACK_KEY_INCONSISTENT` |
| `kind` | string | yes | one of the catalogue kinds, enum name as written (e.g. `COURSE`, `SOUND_EFFECT`) |
| `title` | string or absent | no | display only; absent or blank falls back to `key`; never used for matching |
| `primary` | string | yes | asset digest; must appear in this track's own `parts` |
| `isFlow` | boolean | yes | `true` when the track's parts are switched at sync points |
| `parts` | array | yes | at least the primary; one entry per asset, ordered |
| `timing` | object or null | for flows | required for `isFlow: true`, ignored otherwise |

`parts[]`:

| Key | Type | Rule |
|---|---|---|
| `asset` | string | asset digest; must exist in `assets` |
| `role` | string | `PRE_SHORT`, `PRE`, `BRIDGE`, `MAIN`, `OUTRO` |
| `outro` | integer or null | required for `OUTRO` (`>= 0`), forbidden for every other role; unique per track |

The role and sync-point vocabularies are frozen strings, deliberately not Kotlin
identifier names: renaming a Kotlin enum must not silently change the protocol.

`timing`:

| Key | Type | Required | Meaning |
|---|---|---|---|
| `preludeExit` | sync point | yes | allowed position for leaving a prelude |
| `bridgeEntry` | sync point | yes | allowed position for entering the bridge / handing to Main |
| `outroEntry` | sync point | yes | allowed position for requesting an ending |
| `outroSwitch` | sync point | yes | allowed position for replacing a pending ending |
| `mainRepeats` | integer | yes | >= 1; how many Main passes before an ending is offered |
| `bridgeFadeOutBeats` | number | no, default 0 | >= 0 |
| `bridgeFadeInBeats` | number | no, default 0 | >= 0 |

Sync points: `IMMEDIATE`, `NEXT_BEAT`, `NEXT_BAR`, `NEXT_GRID`, `EXIT_CUE`, `NEXT_CUE`,
`NEXT_CUSTOM_CUE`.

A sync point that is missing or unknown is refused. A default of `IMMEDIATE` would
silence the very distinction the field exists to record.

### 10.5 `stages[]`

| Key | Type | Rule |
|---|---|---|
| `code` | string | non-blank, `[A-Za-z0-9_-]{1,64}`, unique |
| `name` | string or absent | display only; absent or blank falls back to `code` |
| `tracks` | array of strings | logical track keys; every one must exist in `tracks` |

A stage naming more than one track is legal on purpose: a stage can carry its course
theme, a battle arrangement and a tempo variant as separate logical tracks.

### 10.6 `provenance[]`

| Key | Type | Rule |
|---|---|---|
| `subject` | string | track key, stage code, or `pack` |
| `field` | string | the field the record covers, e.g. `timing` |
| `level` | string | `MEASURED_L1`, `MEASURED_L2`, `CHOSEN_BY_PROJECT`, `UNVERIFIED`; unknown values refused |
| `note` | string or null | optional human explanation of where the value came from |

### 10.7 `unlock`

| Key | Type | Rule |
|---|---|---|
| `frozen` | boolean | only `true` can define cup eligibility |
| `stages` | array of strings | non-empty; each must be a stage code present in `stages` |

### 10.8 Example

```json
{
  "formatVersion": 1,
  "packId": "org.example.mkw.base",
  "packVersion": 1,
  "name": "MKW Soundtrack Pack",
  "kind": "FULL",
  "basePackId": null,
  "basePackVersion": null,
  "coverage": {
    "totalLogicalTracks": 2,
    "playableLogicalTracks": 2,
    "codecKinds": [1, 2]
  },
  "assets": [
    {
      "digest": "1111111111111111111111111111111111111111111111111111111111111111",
      "byteSize": 1024,
      "mediaType": "audio/x-bwav",
      "codecKind": 1,
      "sampleRate": 48000,
      "channels": 2,
      "frames": 48000,
      "loopStart": 12000,
      "loopEnd": 48000
    },
    {
      "digest": "2222222222222222222222222222222222222222222222222222222222222222",
      "byteSize": 2048,
      "mediaType": "audio/x-bwav",
      "codecKind": 1,
      "sampleRate": 48000,
      "channels": 2,
      "frames": 24000
    }
  ],
  "tracks": [
    {
      "key": "course:11111111111111111111111111111111",
      "kind": "COURSE",
      "title": "Coaster",
      "primary": "1111111111111111111111111111111111111111111111111111111111111111",
      "isFlow": true,
      "parts": [
        { "asset": "1111111111111111111111111111111111111111111111111111111111111111", "role": "MAIN" },
        { "asset": "2222222222222222222222222222222222222222222222222222222222222222", "role": "OUTRO", "outro": 0 }
      ],
      "timing": {
        "preludeExit": "EXIT_CUE",
        "bridgeEntry": "NEXT_BAR",
        "outroEntry": "NEXT_BAR",
        "outroSwitch": "IMMEDIATE",
        "mainRepeats": 2,
        "bridgeFadeOutBeats": 0.0,
        "bridgeFadeInBeats": 0.0
      }
    }
  ],
  "stages": [
    { "code": "Coaster", "name": "Coaster", "tracks": ["course:11111111111111111111111111111111"] }
  ],
  "provenance": [
    {
      "subject": "course:11111111111111111111111111111111",
      "field": "timing",
      "level": "CHOSEN_BY_PROJECT",
      "note": "section timings are this project's choice; the game's own scheduling is not established"
    }
  ],
  "unlock": { "frozen": true, "stages": ["Coaster"] }
}
```

---

## 11. The other members

### 11.1 `checksums.json`

```json
{
  "formatVersion": 1,
  "members": [
    { "name": "manifest.json", "sha256": "<64 hex>", "bytes": 4321 },
    { "name": "catalog.json", "sha256": "<64 hex>", "bytes": 98765 }
  ]
}
```

Covers every member except itself. `name` must be a legal member name (section 1.2) and
must exist in the archive; a checksum entry for a missing member, or a member with no
entry, is a validation failure.

### 11.2 `catalog.json`

The catalogue the library screen reads. Its entries reference logical track keys, and
each entry repeats `playable` for the *pack's* view of playability (section 6). It is
advisory for display: the manifest is authoritative, and the reader must not let a
catalog entry introduce a track the manifest does not carry.

### 11.3 `music-graphs/<trackSlug>.json`

The compiled playback configuration for one track, referencing asset digests and
`(trackKey, markerId)` cues. It carries no timing of its own: the timings come from the
manifest, so there is exactly one place a sync point can be stated. A graph whose
segments reference a digest the pack does not carry is invalid.

### 11.4 `markers/<stageCode>.json`

Cue/marker data for one stage, reusing the shape of the bundled `cues.json` entry:

```json
{
  "formatVersion": 1,
  "provenance": "L1_MARKER_ONLY",
  "provenanceNote": "Marker records only. The game's scheduling is not established.",
  "code": "Coaster",
  "name": "Coaster",
  "outroCount": 2,
  "hasBridge": true,
  "hasShortPre": true,
  "hasFast": false,
  "cues": [
    { "name": "Full_ToOutro00", "markerId": 12, "frames": 1234567, "sampleRate": 48000 }
  ]
}
```

Rules: cues are ordered by `frames`; a cue with no position is dropped, never placed at
0; an unknown `provenance` is refused (v1 defines only `L1_MARKER_ONLY`); the cue list is
a list of positions, not a schedule, and the UI must say so wherever it is shown.

### 11.5 `routes/<routeId>.json`

Route presets. Stages are referenced by stage code, never by display name or path, for
the same reason saved routes already do that: a name changes, an id does not.

### 11.6 `artwork/<sha256>.<ext>`

Content-addressed images. Allowed extensions: `png`, `jpg`, `webp`. The digest is over
the stored bytes.

---

## 12. Versioning and compatibility

1. **`formatVersion` is the whole document's version.** A reader that sees a version it
   does not implement refuses the pack. It must not parse the part it recognises, because
   a partial read produces a plausible pack whose missing facts become guesses. This is
   the same rule the library file follows when it sets an unknown version aside.
2. A pack claiming `formatVersion: 1` may not rely on any key not defined here. Unknown
   keys are ignored (so a future build can add hints), but any pack whose *behaviour*
   depends on them is invalid for a v1 reader and must declare a higher version.
3. Adding a required key, changing a meaning, or changing an enum value is a new
   `formatVersion`.
4. `packVersion` versions the *content* of one `packId`. It does not version the schema.
   A re-issue that fixes a display name but changes no bytes is still a new
   `packVersion` if it is published as a new file, because a delta must be able to name
   it as a base.

---

## 13. Reader conformance checklist

A reader is conformant only if it does all of the following:

- [ ] refuse a package with no `manifest.json`, or one that is not valid UTF-8/JSON;
- [ ] refuse a `formatVersion` it does not implement, without reading further;
- [ ] refuse an unknown `kind`, an unknown provenance level, an unknown role, an unknown
      sync point, and an unknown track kind;
- [ ] refuse a malformed asset digest anywhere, including in `parts` and `primary`;
- [ ] refuse `playableLogicalTracks > totalLogicalTracks` (at parse time, before any
      other row is trusted);
- [ ] check the declared `totalLogicalTracks`, `playableLogicalTracks` and `codecKinds`
      against the content, and report a mismatch instead of showing the declared number;
- [ ] refuse a timing whose provenance level is `MEASURED_L1` or `MEASURED_L2`;
- [ ] reject member names that escape the archive (absolute, `..`, backslash, unknown
      directory, more than one separator);
- [ ] verify every audio member's name, declared size, and SHA-256 while streaming;
- [ ] verify the extracted asset set equals the declared asset set;
- [ ] run validation and report **every** failure, not the first;
- [ ] install through a staging directory and an atomic rename; never touch the installed
      pack on failure;
- [ ] refuse a delta whose base is absent or different, before writing anything;
- [ ] expose pack-complete and library-complete separately, and never render a percentage
      that would claim 100 % for a partially decodable pack;
- [ ] refuse to satisfy the unlock condition from anything but a frozen, complete,
      fully playable pack.

---

## 14. Validation failure codes

`PackValidation` reports a list of `Failure(code, subject, message)`. Codes are stable;
messages are for people.

| Code | Rule |
|---|---|
| `NO_TRACKS` | the pack carries no logical tracks |
| `ASSET_SIZE_NEGATIVE` | an asset declares a negative byte size |
| `ASSET_EMPTY` | an audio asset is zero bytes |
| `DUPLICATE_IDENTITY` | two assets share a digest, two tracks share a key, or two stages share a code |
| `TRACK_KEY_INCONSISTENT` | a track key is not `kind:first32(primary)` |
| `PRIMARY_NOT_IN_PARTS` | a track's primary asset is not among its own parts |
| `TRACK_ASSET_MISSING` | a track references a digest the asset list does not contain |
| `STAGE_TRACK_MISSING` | a stage references a track key the pack does not contain |
| `FLOW_WITHOUT_MAIN` | a flow has no Main part |
| `NON_FLOW_WITH_MANY_PARTS` | a track that is not a flow carries more than one part |
| `OUTRO_NUMBER_INVALID` | an outro part lacks a number, a non-outro carries one, or two parts share a number |
| `FLOW_TIMING_MISSING` | a flow declares no transition timings |
| `DELTA_WITHOUT_BASE` | a delta names no `basePackId`/`basePackVersion` |
| `FULL_WITH_BASE` | a full pack names a base |
| `DELTA_NOT_FORWARD` | a delta's `packVersion` is not greater than its base version |
| `PROVENANCE_MISSING` | the pack declares no provenance records at all |
| `TIMING_WITHOUT_PROVENANCE` | a track declares timings with no record for them |
| `TIMING_PROVENANCE_OVERCLAIMS` | a timing is labelled `MEASURED_L1` or `MEASURED_L2`; a timing is a scheduling decision, so it must be `CHOSEN_BY_PROJECT` or `UNVERIFIED` |
| `UNLOCK_SCOPE_EMPTY` | the unlock scope lists no stages |
| `UNLOCK_STAGE_UNKNOWN` | the unlock scope names a stage the pack does not describe |
| `UNLOCK_STAGE_INCOMPLETE` | an unlock stage has no track, or its tracks reference assets the pack does not carry |

Coverage blockers, for the cup gate, are reported separately by `PackCoverage`:
`PACK_NOT_FULL`, `NO_UNLOCK_SCOPE`, `SCOPE_NOT_FROZEN`, `SCOPE_STAGE_UNKNOWN`,
`PACK_NOT_COMPLETE`, `UNPLAYABLE_TRACKS`.
