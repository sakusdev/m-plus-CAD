# ADR 0001: 初期Minecraftプラットフォーム

- Status: Proposed
- Date: 2026-07-17
- Issue: [#2 Select initial Minecraft version and mod loader](https://github.com/sakusdev/m-plus-CAD/issues/2)
- Decision owners: architecture / integration

## Context

m+CADはMinecraft Java Editionをブロックベースの3Dモデリング環境として利用し、選択範囲を中立な`StructureSnapshot`へ変換した後、Minecraftから切り離された処理系で`GeneratedScene`を生成し、OBJ、glTF、GLBなどへ出力する。

初期実装では、次の機能を同時に成立させる必要がある。

- クライアント内の選択・プレビュー・設定GUI
- 選択範囲の安全なワールド読取
- Minecraft型を保持しない不変`StructureSnapshot`の生成
- スナップショット生成後のバックグラウンド処理
- FabricまたはNeoForgeの現在の開発環境で実行可能な自動テスト
- 将来のMinecraft更新、別Loader、Blender連携を妨げない境界

このADRはプラットフォームを選ぶが、`mcad-api`、コア、マテリアル、マーカー、エクスポーターの契約をLoader固有にしない。

## Primary-source snapshot

この判断は2026-07-17時点の一次情報に基づく。

1. Minecraft Java Edition 26.2は2026-06-16に正式リリースされた。
2. Fabricは26.2向けの公式対応記事を公開し、記事執筆時点の開発環境としてLoom 1.17、Gradle 9.5.1、Fabric Loader 0.19.3を案内している。
3. Minecraft 26.1以降は難読化されておらず、FabricはYarnを公式サポート対象から外し、Mojang公式名へ移行している。26.1の開発にはJava 25が必要と案内されている。
4. Fabric公式ドキュメントは、物理クライアント限定のmod環境、クライアントentrypoint、Minecraft標準`Screen`によるカスタムGUI、Fabric Loader JUnit、サーバー／クライアントGameTestを文書化している。
5. Minecraft 26.2は実験的Vulkanバックエンドを追加した。Fabricはraw OpenGLではなくMinecraftのレンダリング抽象を使用するよう案内している。
6. NeoForgeの公式ドキュメントは判断日時点で26.1を現行ドキュメントとして提示し、Java 25を要求している。公式ニュース上の最新プラットフォーム対応告知も26.1である。

参照URLは末尾の[Sources](#sources)に記載する。

## Decision

### 1. Initial target

初期ターゲットを次のとおり決定する。

| 項目 | 決定 |
| --- | --- |
| Minecraft | Java Edition 26.2 |
| Loader | Fabric |
| 配布形態 | 物理クライアント専用mod |
| Java | 64-bit JDK 25 |
| 名前・マッピング | 難読化されていないMojang公式名 |
| GUI | Minecraft標準`Screen`と標準Widgetを第一選択にする |
| テスト | 純粋JUnit + Fabric Loader JUnit + Fabric GameTest |

Minecraftの互換範囲は最初から広い`26.x`指定にせず、26.2を明示的な対応対象とする。MinecraftはSemantic Versioningではなく、minor相当の更新でもGUI、描画、ワールド、登録処理が変わり得るためである。

Loom、Gradle、Fabric Loader、Fabric APIの正確な依存バージョンはIssue #3で再確認して固定する。このADRに記載したバージョンは判断時点の根拠であり、ビルド依存を変更する指示ではない。

### 2. Loader boundary

Fabric固有コードは`mcad-minecraft`内のFabricアダプターに限定する。

次の領域へFabric、Minecraft、Mixin、Loader固有型を公開しない。

- `mcad-api`
- `mcad-core`
- `mcad-materials`
- `mcad-markers`
- エクスポーターモジュール
- Blenderアドオン

基本パイプラインは変更しない。

```text
Minecraft ClientLevel
        │  Fabric/Minecraft adapter
        ▼
immutable StructureSnapshot
        │  loader-independent processing
        ▼
GeneratedScene
        │
        ▼
Exporter
```

将来NeoForge対応を追加する場合も、同じ中立APIへ変換する別アダプターとして実装する。FabricとNeoForgeの共通化のためにLoader固有型を`mcad-api`へ移動してはならない。

### 3. Client-only responsibilities

MVPは物理クライアント専用とし、専用サーバーへの導入を要求しない。

クライアント側の責務は次に限定する。

- 二点選択と選択範囲の表示
- 設定GUI
- クライアントが保持しているロード済みワールド状態の読取
- Minecraftのブロック識別子・状態値を中立値へ変換
- 進捗、キャンセル、診断の画面表示
- 生成・エクスポート処理の起動と結果通知

単一プレイヤーでもクライアントと統合サーバーは論理的に分離されている。MVPは統合サーバーの内部ワールドへ直接到達せず、クライアントが利用可能な状態だけを読む。

その結果、MVPには次の制限がある。

- 未ロードchunkを自動ロードまたは生成しない
- クライアントへ同期されていない情報を推測しない
- マルチプレイヤーでサーバー非公開のNBTや状態を取得しない
- 選択範囲に未ロード領域が含まれる場合は、暗黙に欠落させず診断またはエラーにする

サーバー権限が必要な将来機能は、任意のserver companionまたは統合サーバー専用アダプターとして別途設計する。

### 4. Threading and background work

Minecraftのライブワールド参照と、重い中立処理を明確に分離する。

1. GUI操作、選択更新、ライブワールド読取はクライアントのゲームスレッド上で行う。
2. 大きな選択は、1フレームで無制限に走査せず、時間またはブロック数で上限を設けた段階的コピーを行う。
3. 読み取ったMinecraft値は、その場でcanonical block identifier、順序付きstate properties、整数相対座標などの中立値へ変換する。
4. 完成した不変`StructureSnapshot`だけをworker executorへ渡す。
5. メッシュ生成、マテリアル解決、マーカー解釈、preflight、ファイル出力は原則としてworker threadで実行する。
6. 進捗・完了・エラーのGUI反映はクライアントスレッドへ戻す。
7. キャンセル時は未完成Snapshotを公開せず、エクスポーターは一時ファイルを削除する。

worker threadへ渡してはならないものの例：

- `Minecraft`
- `ClientLevel`
- `BlockState`
- registry handle
- chunk、block entity、entityのMinecraftインスタンス
- Minecraftのmutable collection

この制約は`docs/contracts/structure-snapshot.md`の「Snapshot完成後だけoff-thread処理可能」という境界を実装上も維持する。

### 5. GUI approach

MVPの設定画面はMinecraft標準`Screen`、標準Widget、標準ナビゲーションを使う。

Fabric APIは次の用途に必要最小限で使用する。

- client entrypoint
- key mapping、client lifecycle、screen hook
- 選択プレビューに必要な描画event
- client GameTest

第三者GUIライブラリはMVPの必須依存にしない。理由は次のとおり。

- 設定モデルは中立`ProjectSettings`であり、GUIライブラリのモデルと結合させないため
- Minecraft更新時の追従対象を減らすため
- 外部ライブラリの26.2対応待ちを避けるため
- アクセシビリティ、キーボード操作、画面スケーリングをMinecraft標準挙動へ寄せるため

GUIクラス自体はMinecraft依存でよいが、編集対象は不変設定snapshotを生成する中立editor modelとする。出力形式の変更で他設定を書き換えてはならない。

### 6. Rendering approach

選択枠やプレビューはMinecraft/Fabricの現行レンダリング抽象を使う。

- raw OpenGL呼出しを追加しない
- OpenGL専用状態を中立APIへ漏らさない
- `RenderPipeline`、`RenderState`、Blaze3Dなど、そのMinecraft版で公式に提供される抽象を使う
- OpenGL/Vulkanの選択に依存しない描画経路を優先する
- 描画用一時データと`GeneratedScene`を同一モデルとして扱わない

26.2でVulkanが実験導入され、将来的にOpenGL削除が予定されているため、raw OpenGL依存は初期実装から禁止する。

### 7. Mapping and source names

26.2ではMojang公式名を使用する。

- Yarnを新規導入しない
- intermediary名をプロジェクト内の設計用語や中立識別子にしない
- Minecraftクラス名を`mcad-api`の型名へ転写しない
- Mixinが必要な場合も、公開面ではなくFabricアダプター内部へ限定する
- まずFabric APIのeventや公開hookを検討し、Mixinは必要性を説明できる場合だけ使用する

これにより、26.1以前のYarnベースから開始して直後に公式名へ全面移行するコストを避ける。

### 8. Test strategy

テストを三層に分ける。

#### Layer A: Minecraftを起動しない純粋テスト

対象：

- `mcad-api`のvalidationとimmutability
- Snapshot fixtureのcanonicalization
- mesh、materials、markers
- exporter preflightとserialization
- 決定性、境界値、不正入力、NaN/Infinity、不正index
- progress、cancellation、path safety

通常のJUnit 5で実行し、CIの主テスト層とする。MinecraftやFabricをclasspathに要求しないモジュールを最大化する。

#### Layer B: Fabric Loader JUnit

対象：

- MinecraftクラスまたはMixin変換済み環境を必要とする小さなadapter helper
- canonical block identifier/state conversion
- Loader metadataやclient-only境界に関するテスト

純粋テストで代替可能なものをこの層へ移さない。

#### Layer C: Fabric GameTest

対象：

- test worldからのSnapshot生成
- 負の座標、chunk境界、未ロード領域の診断
- selection lifecycle
- client GUIの起動と基本操作
- world previewのsmoke test
- client threadからSnapshotを完成させ、以後Minecraft参照なしで処理できること

server GameTestとclient GameTestを用途別に使う。クライアントGameTestは必要に応じてXvfbを使用する。スクリーンショットはレイアウト回帰の補助とし、論理テストの代わりにはしない。

専用サーバー向けには、client-only modが誤って共通初期化を実行しないことをmetadataまたは起動smoke testで確認する。

### 9. Compatibility and upgrade policy

初期リリースはMinecraft 26.2専用とする。

次版への移行は次の手順で行う。

1. Mojangの正式リリースを確認する。
2. Fabric Loader、Loom、必要なFabric API moduleが正式対応していることを確認する。
3. GUI、world read、rendering、GameTestに必要なhookの利用可否を確認する。
4. `mcad-minecraft`とビルドmetadataを更新する。
5. Snapshot fixtureとGeneratedScene fixtureが論理的に不変であることを確認する。
6. API変更が必要に見える場合は、Loader差分をadapterで吸収できない理由を先に審査する。
7. 対応範囲を広げる場合も、未検証のMinecraft版をversion rangeへ含めない。

通常のMinecraft更新で変更してよいのはFabric/Minecraftアダプターとビルド配線であり、`StructureSnapshot`や`GeneratedScene`の中立契約変更は自動的には正当化されない。

## Alternatives considered

### NeoForge 26.1を初期Loaderにする

NeoForgeもJava 25、クライアント機能、実行構成、テスト環境を提供しており、技術的に実現可能である。

採用しない理由：

- 判断日時点で公式ドキュメントとプラットフォーム対応告知が26.1を中心としており、Minecraft 26.2向けの明確な公式リリース案内を確認できなかった
- Fabricには26.2向けの具体的なLoom、Gradle、Loader案内が存在した
- m+CADのMVPはクライアントGUI、client lifecycle、world previewが中心で、Fabricの小さいclient adapterで必要範囲を満たせる

NeoForgeを否定する決定ではない。需要と保守能力が確認できた時点で第二アダプター候補とする。

### FabricでMinecraft 26.1.2を対象にする

公式ドキュメントとexample modが整っており、26.2より資料が安定している利点がある。

採用しない理由：

- 26.2がすでに正式リリース済みである
- Fabricが26.2向け開発toolchainを案内済みである
- 26.1から始めると、GUI再編やVulkan対応を含む26.2移行を実装開始直後に行う可能性が高い

### Minecraft 1.21.11以前を対象にする

Java 21や既存mod資産の多さは利点になり得る。

採用しない理由：

- 26.1で難読化とmapping方針が大きく変わった後に、旧方式から開始することになる
- Yarnから公式名への移行を短期間で行う二重コストが発生する
- 最新版対応を目的とする新規プロジェクトとしては移行負債が大きい

### MVPからFabricとNeoForgeを同時対応する

採用しない理由：

- world、GUI、rendering、lifecycle、test配線を二重に実装する必要がある
- API契約が未成熟な段階で「共通化のための抽象」を先に増やす危険がある
- まず一つのadapterで中立境界が十分か検証した方が、将来の第二adapterを小さくできる

### client/server共通modとして配布する

採用しない理由：

- 専用サーバーへの導入を要求すると、クライアント側モデリングツールとしての導入障壁が上がる
- マルチプレイヤーでサーバー権限や通信protocolが必要になる
- MVPの選択、GUI、ロード済み状態のexportはclient-onlyで成立する

将来、未ロードchunk、サーバーのみが持つmetadata、共同編集が必要になった場合はoptional server companionを別ADRで検討する。

### 第三者GUIフレームワークを必須採用する

採用しない理由：

- Minecraft 26.2対応状況が追加のブロッカーになる
- `ProjectSettings`がGUIライブラリ固有modelへ引きずられる危険がある
- MVPのフォーム、タブ、validation、診断表示は標準`Screen`とWidgetで実装可能である

## Consequences

### Positive

- 現行Minecraft 26.2を初期対応にできる
- Fabricの明示的な26.2 toolchain guidanceを利用できる
- 公式名を使い、Yarnからの移行負債を持たない
- client-onlyで導入でき、専用サーバーを要求しない
- Minecraft依存をSnapshot境界より手前へ閉じ込められる
- 大部分のテストをMinecraft起動なしで高速に実行できる
- 将来のNeoForge adapterやMinecraft更新が中立APIへ波及しにくい
- OpenGL/Vulkan切替に耐える描画方針を初期から採用できる

### Negative

- 開発者とCIにJava 25が必要になる
- 26.2のGUI・描画APIは比較的新しく、追加の変更が起きる可能性がある
- exact-version方針によりMinecraft更新ごとの検証とリリース作業が必要になる
- client-only MVPは未ロードchunkや未同期server dataをexportできない
- MVPではNeoForge利用者を直接サポートしない
- GameTestは純粋JUnitより遅く、クライアントテストには描画環境が必要になる

## Migration triggers

次のいずれかが発生した場合、このADRを再検討し、必要なら後続ADRで置き換える。

- Minecraftの次の正式版に対してFabricの安定版toolchainと必要APIが揃った
- 26.2または採用Fabric版に重大なsecurity問題・互換問題がある
- Fabricが26.2を保守対象から外した
- Java最低要件が変わった
- GUI、world read、rendering、GameTestに必要なAPIが削除または非推奨化された
- Vulkanが既定になり、preview renderingの再設計が必要になった
- client-onlyでは満たせないserver-authoritative export要件が承認された
- NeoForge対応の利用者需要と継続保守担当が確保された
- 第二Loaderを追加しても中立APIを変更せずに実装できることを検証する必要が生じた

## Out of scope

このADRでは次を実装・決定しない。

- Gradleマルチモジュール構成
- 正確な依存versionの固定
- `mcad-api`のclass構成
- 選択、GUI、Snapshot、renderingの実装
- Fabric API moduleの最終選択
- Mixin injection point
- NeoForge adapter
- server companionまたはnetwork protocol
- Minecraft公式素材の読取、抽出、複製、同梱、出力

## Sources

すべて2026-07-17に確認した公式一次情報。

- Mojang Studios, [Minecraft Java Edition 26.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2), 2026-06-16.
- Fabric, [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html), 2026-06-15.
- Fabric, [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html), 2026-03-14.
- Fabric Documentation, [Setting Up Your Development Environment](https://docs.fabricmc.net/develop/getting-started/setting-up).
- Fabric Documentation, [Project Structure](https://docs.fabricmc.net/develop/getting-started/project-structure).
- Fabric Documentation, [fabric.mod.json](https://docs.fabricmc.net/develop/loader/fabric-mod-json).
- Fabric Documentation, [Custom Screens](https://docs.fabricmc.net/develop/rendering/gui/custom-screens).
- Fabric Documentation, [Basic Rendering Concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts).
- Fabric Documentation, [Automated Testing](https://docs.fabricmc.net/develop/automatic-testing).
- Fabric Documentation, [Networking](https://docs.fabricmc.net/develop/networking).
- Fabric Documentation, [Events](https://docs.fabricmc.net/develop/events).
- FabricMC Javadocs, [ClientTickEvents](https://maven.fabricmc.net/docs/fabric-api-0.97.9%2B1.21/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.html).
- NeoForged Documentation, [Getting Started with NeoForge](https://docs.neoforged.net/docs/gettingstarted/).
- NeoForged, [News](https://neoforged.net/news/).
