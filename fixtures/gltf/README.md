# glTF fixtures

`basic-triangle.gltf` と `basic-triangle.bin` は、Minecraft素材を含まない中立な三角形Sceneを `GltfExporter` で出力した最小fixtureです。

検証対象:

- glTF 2.0 asset/version
- external binary buffer
- 4-byte aligned buffer views
- POSITION/NORMAL/indices accessors
- POSITIONとindicesのmin/max bounds
- stable m+CAD IDを保持するextras
- Scene originとconfigured export transformの明示的なmetadata

## Khronos glTF Validator

Khronos Group公式のnpm package `gltf-validator` version `2.0.0-dev.3.10`で検証しました。

```text
Errors:   0
Warnings: 0
Infos:    0
Hints:    0
```

詳細は `basic-triangle.report.json` に固定しています。再検証時は同じdirectoryでfixtureとbufferを配置し、公式validatorへ`basic-triangle.gltf`を渡してください。

module testでもJSON/GLB header、chunk length、alignment、accessor参照、buffer lengthを検査します。
