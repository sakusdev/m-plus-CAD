# m+CAD

**Minecraftを、3Dモデラーに。**

m+CADは、Minecraft内で組み立てた構造を、Blenderなどで編集できる汎用3Dデータへ変換するオープンソースのclient-only Fabric Modです。Minecraftのブロックは最終的な見た目ではなく、形状・グループ・原点・ライト・カーブ・ボーンなどを入力するための直感的なモデリングUIとして扱います。

## できること

- Minecraft 26.2 / Fabric上で2点を指定して構造を選択
- 選択範囲をワールド内に表示
- ブロック種類ごとに独立したmeshを生成
- 隣接ブロック間の隠れた面を除去
- GLB、glTF + BIN、OBJ + MTLへ出力
- **Blenderへlocalhost WebSocketでリアルタイム差分同期**
- stable IDを使ったMesh、Material、Node、Light、Camera、Curve、Bone、Collisionのupsert/remove
- Blender切断後の自動再接続とfull resync
- deterministic identification colour、単色、頂点カラーをGUIから選択
- 中立material moduleからユーザー定義mappingを適用可能
- 原点、倍率、回転、座標軸、collision、marker、loss policyをGUIから設定
- Snapshot取得、mesh生成、書き出しの進捗表示とキャンセル
- 設定のversioned保存・migration
- safe temporary output、rollback、決定的serialization

Minecraft/Mojang/Microsoftの公式テクスチャ、モデルJSON、音声、registry dumpなどは、同梱・抽出・複製・出力・Live Link送信しません。

## Minecraft操作

初期キーバインドは次のとおりです。Minecraftの「操作設定」から変更できます。

| キー | 操作 |
| --- | --- |
| `G` | 照準を合わせたblockを選択の始点にする |
| `H` | 照準を合わせたblockを選択の終点にする |
| `J` | 選択を解除する |
| `O` | m+CAD設定画面を開く |
| `P` | 現在の設定でexportを開始する |
| `K` | 実行中の処理をキャンセルする |
| `L` | Blender Live Linkを開始・停止する |
| `I` | Blenderへ即時同期する |

### ファイルExport

1. ワールド内で構造の一方の端に照準を合わせて`G`を押します。
2. 反対側の端に照準を合わせて`H`を押します。
3. 表示された選択範囲を確認します。
4. `O`で出力形式、出力名、material、原点、倍率などを設定します。
5. 設定画面の「エクスポート開始」または`P`で書き出します。

生成物はゲームディレクトリ内の`m-plus-cad/`以下へ出力されます。既定のGLBは`m-plus-cad/exports/model.glb`です。設定は`config/m-plus-cad/settings.mcad`へ保存されます。

## Blender Live Link

1. CI artifactまたはReleaseの`m-plus-cad-blender-addon.zip`をBlenderへインストールします。
2. Minecraftで選択を作り、`L`または設定画面からLive Linkを開始します。
3. m+CAD画面に表示されるportと32桁session tokenを、Blenderの`3D View > Sidebar > m+CAD`へ入力します。
4. Blenderで「接続」を押します。
5. 初回はScene全体、その後は約1秒間隔で変更されたstable IDだけが同期されます。

通信serverは`127.0.0.1`のみにbindし、token不一致の接続をWebSocket upgrade前に拒否します。v1はMinecraft→Blenderの一方向同期であり、Blenderからworldのblockを変更しません。

- [Blender add-on guide](blender-addon/README.md)
- [Live Link protocol v1](docs/live-link-protocol.md)

## ビルド

Java 25を使用します。

```text
./gradlew clean build
```

実行可能なFabric Mod JARは`mcad-minecraft/build/libs/`へ生成されます。CIでは次のartifactを保存します。

- `m-plus-cad-fabric`
- `m-plus-cad-blender-addon`

## アーキテクチャ

```text
Minecraft live world
  -> immutable StructureSnapshot
  -> marker interpretation
  -> neutral mesh/material processing
  -> immutable GeneratedScene
      -> GLB / glTF / OBJ exporter
      -> versioned differential Live Link protocol
          -> authenticated localhost WebSocket
              -> Blender add-on / m+CAD Live Collection
```

Minecraft/Fabric固有型は`mcad-minecraft`から外へ漏らしません。ExporterとLive Link encoderはMinecraft worldへアクセスせず、`GeneratedScene`だけを入力にします。

- [Architecture](ARCHITECTURE.md)
- [Agent contribution rules](CONTRIBUTING_AGENTS.md)
- [Roadmap](ROADMAP.md)
- [Contracts](docs/contracts/)

## ライセンス

ソースコードは **Mozilla Public License 2.0 (MPL-2.0)** で公開します。m+CADで生成したモデルが、m+CADを使ったという理由だけでMPL-2.0の対象になることはありません。

`m+CAD`の名称・ロゴと、第三者の商標・素材はコードライセンスの対象外です。

## 非公式プロジェクト

m+CADはMojang StudiosおよびMicrosoftとは関係のない独立プロジェクトです。MinecraftはMicrosoftの商標です。
