# tools/

Verification helpers used to derive and prove the facts in `evidence/`.

None of these are part of the shipped app. They read the user's own extracted
files at paths given on the command line and write nothing back to them.

| Tool | Purpose |
|---|---|
| `dsp_verify.py` | Independent DSP-ADPCM decoder. Decodes a `.bwav` and compares sample for sample against the reference `.wav` render. |
| `dsp_verify_all.py` | Runs `dsp_verify` across several tracks at head/middle/tail windows and reports exact-match counts. |
| `bwav_layout.py` | Measures the BWAV data layout over the whole corpus (block sizing, channel alignment, padded vs truncated tail). |
| `graph_build.py` | Emits a minimal compiled playback graph (v1..v13 wire format) for a single asset. |
| `graph_parse.py` | Reads a compiled graph back and validates magic, body length and CRC32. |

## Why they live here

The layout rules the engine now implements were not read from a specification;
they were measured. These scripts are the measurement, kept so the result can be
re-checked instead of trusted.

`dsp_verify.py` is deliberately a second, independent implementation of the same
codec. It agreeing with the native engine at maxdiff 0 is what makes the decoder
claim credible, because agreement between an implementation and itself proves
nothing.

## Paths

Each script takes explicit paths. The defaults point at the author's extracted
files and will not exist elsewhere; pass your own. No game data is stored in this
repository.
