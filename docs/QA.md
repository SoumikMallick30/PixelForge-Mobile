# QA scorecard

## Automated/static checks

- Model ZIP extraction rejects path traversal.
- Installation requires every graph, tokenizer file, a format version, and a
  non-empty license field before replacing the current model.
- Generation runs away from the main thread and remains cancellable.
- The manifest requests no storage, camera, microphone, or location access.
- Saving uses scoped MediaStore storage; sharing grants temporary read access.
- Release shrinking preserves ONNX Runtime JNI classes.

## Device acceptance test — Galaxy S23

1. Install a debug APK on an S23 with at least 8 GB free storage.
2. Import a validated version-1 FP16 model pack and restart the app.
3. Enable airplane mode. Generate from a short portrait prompt.
4. Verify progress changes, a 512×512 preview appears, and no process crash or
   low-memory kill occurs.
5. Save, then confirm the PNG under `Pictures/PixelForge`.
6. Share to a local app and verify the receiving app can read the PNG.
7. Add a landscape reference, enter a transformation prompt, and generate.
8. Confirm the output follows the reference composition and prompt.
9. Generate again and confirm a different result is produced.
10. Run Android Studio's profiler and record peak memory, total time, battery
    drain, and thermal throttling after three consecutive generations.

Device performance/thermal checks cannot be replaced by an emulator.
