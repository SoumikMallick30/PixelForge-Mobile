# PixelForge model-pack format (version 1)

A model pack is a ZIP whose root contains:

```
model.json
text_encoder/model.onnx
unet/model.onnx
vae_encoder/model.onnx
vae_decoder/model.onnx
tokenizer/vocab.json
tokenizer/merges.txt
```

`model.json` must contain the model identity and license so PixelForge can
retain attribution with the installed files:

```json
{
  "format": 1,
  "name": "DreamShaper 8 ONNX mobile pack",
  "source": "https://huggingface.co/Lykon/dreamshaper-8",
  "license": "CreativeML Open RAIL-M"
}
```

## Graph contract

- CLIP text encoder: `input_ids` int64 `[1,77]`, float output `[1,77,768]`.
- U-Net: float inputs named like `sample`, `timestep`, and
  `encoder_hidden_states`; noise output `[1,4,64,64]`.
- VAE encoder: float image `[1,3,512,512]`; moments or latent output. If eight
  moment channels are returned, PixelForge uses the first four (mean).
- VAE decoder: float latent `[1,4,64,64]`; image output `[1,3,512,512]`.

Use static 512-pixel shapes and FP16 weights where supported. External-data
ONNX models must be consolidated or include all referenced data within the
same component directory before zipping.

Do not distribute a converted pack until you have verified that conversion,
redistribution, attribution, and intended use comply with the source model's
license. PixelForge intentionally does not download or redistribute weights.
