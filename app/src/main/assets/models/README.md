# `assets/models/`

This directory is a **placeholder** for the on-device LLM model
deliverable. The model itself is **never** committed — the
`.gitignore` at the repo root excludes `*.gguf` and `models/*.gguf`
intentionally.

## Where the model actually lives

The model file is downloaded at first run to:

```
<context.filesDir>/models/qwen3-1.7b-q4_k_m.gguf
```

i.e. the app's private internal storage. The download is kicked
off by `com.baton.app.ai.llama.ModelManager.download()` (v1.4.2
F-10), which streams a GGUF file from a hard-coded placeholder URL
(see `ModelManager.DEFAULT_MODEL_URL`) and writes it to that path.

The first-run download UX is rendered by
`com.baton.app.ui.llama.ModelDownloadScreen`, exposed as
`MODEL_DOWNLOAD_ROUTE` for the parent session's `NavHost`.

## Why the directory exists

The empty `assets/models/` directory is committed so the path
appears in a fresh checkout, making it obvious to anyone reading
the repo "this is where the model would be, if we shipped it
pre-bundled". A future M3+ track may swap the runtime download
for Play asset delivery or a pre-installed APK asset pack; the
delivery mechanism changes, the destination path does not.

## What goes here (someday, maybe)

A pre-bundled GGUF would be named to match the existing
`ModelManager.modelFile()` path:

- `qwen3-1.7b-q4_k_m.gguf` — the Qwen 3 1.7B Q4_K_M GGUF used by
  the on-device extraction path
  (`com.baton.app.ai.extraction.Extractor`).

Until that decision is made, this file is fetched on first run and
cached locally. See `app/src/main/assets/model_url.txt` and
`app/src/main/assets/model_sha256.txt` for the production URL and
SHA-256 used by the legacy `ModelManager.downloadModel()` flow.
