# m+CAD Live Link Protocol v1

m+CAD Live Linkは、Minecraft client Modで生成した中立`GeneratedScene`をBlenderへ同期するlocalhost専用protocolです。

## Transport

- WebSocket RFC 6455
- IPv4 loopback `127.0.0.1`のみにbind
- 既定port: `8765`
- path: `/mcad`
- query: `token=<128-bit hexadecimal session token>`
- UTF-8 text frameのみ
- BlenderからMinecraft worldを変更するcommandはv1に存在しない

接続例:

```text
ws://127.0.0.1:8765/mcad?token=0123456789abcdef0123456789abcdef
```

session tokenはLive Link開始ごとに再生成されます。不正tokenはWebSocket upgrade前に`401 Unauthorized`で拒否されます。

## Envelope

```json
{
  "protocol": "mcad-live-link",
  "version": 1,
  "type": "scene-update",
  "sceneId": "scene/example",
  "revision": 42,
  "full": false,
  "scene": {},
  "upsert": {},
  "remove": {}
}
```

`revision`はsession内で単調増加します。clientは古いrevisionを無視できます。

## Full sync

新規接続時、またはscene ID変更時は`full: true`です。Blender add-onはm+CAD所有Collectionをclearしてから、全upsert要素を適用します。

## Differential sync

通常更新は`full: false`です。各要素はstable IDで比較され、JSON正規表現が変化した要素だけが`upsert`へ入ります。前回存在し今回存在しないstable IDは`remove`へ入ります。

対象bucket:

- `materials`
- `meshes`
- `nodes`
- `lights`
- `cameras`
- `curves`
- `bones`
- `collisions`

適用順は、remove後にmaterial → mesh → node → light/camera/curve/bone/collisionです。

## Scene clear

選択が解除された場合:

```json
{
  "protocol": "mcad-live-link",
  "version": 1,
  "type": "scene-clear",
  "revision": 43,
  "reason": "selection-unavailable"
}
```

## Mesh payload

Meshはprimitive単位でpositions、normals、triangle indices、material ID、任意vertex colourを送ります。Minecraft texture、model JSON、registry dump、その他Mojang/Microsoft assetはprotocolへ含めません。

## Threading

Minecraft live worldの読取はclient thread上で小分けに実行されます。完了したimmutable `StructureSnapshot`だけをworkerへ渡し、mesh/material/marker処理とprotocol encodeを行います。network accept/read/writeはdaemon threadで実行されます。

## Compatibility

v1 clientは未知fieldを無視してください。互換性を破る変更は`version`を増やし、Minecraft側とBlender側の両方で明示的に対応します。
