# PixelForge Mobile

PixelForge is a deliberately simple, open-source Android image generator:

1. **Describe → Generate → image**
2. **Add reference → describe → Generate → image**

There are no seed, CFG, sampler, scheduler, step, denoise, inpainting, LoRA,
checkpoint, or style controls. PixelForge applies tested internal defaults and
prompts its pipeline toward photographic output.

## Status

This repository contains the Android application, text-to-image and img2img
pipelines, CLIP BPE tokenizer, DDIM scheduler, model importer, progress UI,
preview, Save, Share, and Generate Again. A compatible ONNX model pack is not
bundled for size and licensing reasons. The first launch asks the user to pick
one ZIP; no account or paid service is involved.

### Verified build

The project was compiled with JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3,
and Android SDK 35. `assembleDebug`, scheduler unit tests, and `lintDebug` pass.
The remaining lint notices only report that newer optional dependency versions
are available. Model inference and thermal performance still require the model
pack and physical Galaxy S23 acceptance test described in `docs/QA.md`.

## Target device and requirements

- Samsung Galaxy S23 (Snapdragon 8 Gen 2, 8 GB RAM) is the reference target.
- Android 10 / API 29 or newer; Android 13+ recommended.
- Roughly 4–8 GB free storage depending on model precision/export.
- Expect sustained heat and multi-minute CPU/NNAPI generation until the graphs
  are specifically quantized/optimized for the device. Close memory-heavy apps.
- A real S23 is required for meaningful latency, memory, and thermal validation.

The default pipeline produces 512×512 images in 20 hidden DDIM steps. NNAPI is
requested with ONNX Runtime CPU fallback. Exact accelerator coverage depends on
the operators and shapes in the imported graph.

## Architecture

```
Jetpack Compose UI
  └─ PixelForgeViewModel (state, progress, cancellation)
      ├─ ModelStore (validated, private model import)
      └─ DiffusionEngine
          ├─ CLIP BPE tokenizer + ONNX text encoder
          ├─ ONNX U-Net + internal DDIM/guidance defaults
          └─ ONNX VAE encoder/decoder (reference and output)
```

Kotlin and Jetpack Compose own the app/UI. ONNX Runtime for Android runs all
model graphs locally. Model files live in app-private storage.

## Build

Prerequisites: JDK 17, Android SDK 35, Android build-tools, and Gradle 8.9.

```text
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the repository root in a current Android Studio release for the easiest
setup. See [the model-pack contract](docs/MODEL_PACK.md), create/import a
license-compatible pack, then follow [the S23 QA checklist](docs/QA.md).

## Privacy and offline behavior

Generation, prompt processing, reference decoding, and model inference happen
on device. Prompts and references are not uploaded. The app declares no
internet permission and imports a user-selected local ZIP, so the installed
application can be audited as fully air-gapped.

Generated images are private until the user taps **Save** or **Share**. Save
uses Android's scoped `Pictures/PixelForge` collection. Share creates a
temporary cached PNG and grants only the selected receiving app read access.

## Runtime and model licensing

- App source: Apache License 2.0 (see `LICENSE`).
- [ONNX Runtime](https://github.com/microsoft/onnxruntime): MIT License. It is
  consumed from Maven Central and not modified here.
- Suggested baseline: [DreamShaper 8](https://huggingface.co/Lykon/dreamshaper-8),
  marked CreativeML Open RAIL-M by its publisher. This is an open-weight model
  with use restrictions, not an OSI-style unrestricted software license.

Model weights are deliberately excluded. Anyone preparing or redistributing a
pack must read and preserve the model license, restrictions, model card, and
attribution. The pack importer requires license/source metadata; this is useful
recordkeeping, not a substitute for legal review.

## Limitations

- “Photorealism-only” is product/UI intent and prompt conditioning, not a hard
  mathematical guarantee. A diffusion model can still produce stylized or
  flawed results.
- Reference mode is img2img, not face-ID preservation; it keeps broad content
  and composition but may change identity and fine details.
- No safety classifier is bundled. Distributors should assess their audience,
  local law, store policy, and the selected model's acceptable-use terms.
- Mobile diffusion is memory/thermal intensive. FP32 packs are unlikely to be
  practical on an 8 GB phone; tested FP16/static-shape exports are preferred.
- Model conversion is reproducibility-sensitive. Validate tensor names, output
  layouts, and images before publishing a pack.

## Contributing

Keep the user workflow simple. Performance changes, device-specific execution
providers, accessibility improvements, and reproducible model-pack tooling are
welcome; advanced generation controls should remain internal.
