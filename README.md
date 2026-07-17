# m+CAD

**Minecraftを、3Dモデラーに。**

m+CADは、Minecraft内で組み立てた構造を、Blenderなどで編集できる汎用3Dデータへ変換するオープンソースプロジェクトです。Minecraftのブロックは最終的な見た目ではなく、形状・グループ・原点・ライト・カーブ・ボーンなどを入力するための直感的なモデリングUIとして扱います。

## 基本方針

- ブロック種類ごとに別メッシュ化できる
- 設定はMinecraft内GUIから変更できる
- Minecraft公式テクスチャは同梱・抽出・出力しない
- 単色、頂点カラー、ユーザー定義素材など独立したマテリアル方式を使う
- OBJ、glTF/GLBなどの出力形式は共通の中間データモデルを利用する
- Minecraft依存コードとメッシュ生成・エクスポート処理を分離する
- 並列開発時は担当パスと公開APIを固定し、統合PRを経由する

## 現在の状態

設計基盤を構築中です。実装担当は、作業を始める前に以下を読んでください。

- [Architecture](ARCHITECTURE.md)
- [Agent contribution rules](CONTRIBUTING_AGENTS.md)
- [Roadmap](ROADMAP.md)
- [Contracts](docs/contracts/)

## ライセンス

ソースコードは **Mozilla Public License 2.0 (MPL-2.0)** で公開します。m+CADで生成したモデルが、m+CADを使ったという理由だけでMPL-2.0の対象になることはありません。

`m+CAD`の名称・ロゴと、第三者の商標・素材はコードライセンスの対象外です。

## 非公式プロジェクト

m+CADはMojang StudiosおよびMicrosoftとは関係のない独立プロジェクトです。MinecraftはMicrosoftの商標です。
