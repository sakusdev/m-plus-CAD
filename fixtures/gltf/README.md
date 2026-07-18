# glTF fixtures

`basic-triangle.gltf` と `basic-triangle.bin` は、Minecraft素材を含まない中立な三角形Sceneを `GltfExporter` で出力した最小fixtureです。

検証対象:

- glTF 2.0 asset/version
- external binary buffer
- 4-byte aligned buffer views
- POSITION/NORMAL/indices accessors
- POSITIONとindicesのmin/max bounds
- stable m+CAD IDを保持するextras

Khronos glTF-Validator CLIが利用可能な環境では、次のように検証できます。

```text
gltf_validator -r basic-triangle.report.json basic-triangle.gltf
```

validator binaryを利用できない環境でも、module testがJSON/GLB header、chunk length、alignment、accessor参照、buffer lengthを検査します。
