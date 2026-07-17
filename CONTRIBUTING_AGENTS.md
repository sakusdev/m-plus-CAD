# Parallel Agent Contribution Rules

この文書は、人間と複数のAIエージェントが同じリポジトリを並列で変更するための運用契約です。

## 1. 基本原則

- 1チャットにつき1 Issue、1ブランチ、1責務を原則とする。
- 各担当は許可されたパスだけを変更する。
- 公開API、ルートビルド、依存バージョン、初期化配線は統合担当だけが変更する。
- 既存コードの無関係なリファクタリングをしない。
- Draft PRを作成し、勝手にmergeしない。
- 仕様上の疑問は推測で埋めず、Issueへ提案として残す。ただし担当範囲内で安全に進められる部分は実装を続ける。

## 2. ブランチ

```text
agent/<short-responsibility>
```

例：

```text
agent/world-snapshot
agent/mesh-engine
agent/obj-exporter
```

通常の実装PRは、その時点で指定された統合ブランチをbaseにする。統合ブランチがまだ存在しない初期段階では`main`をbaseにする。

## 3. 作業開始手順

1. 担当Issueを読む。
2. `ARCHITECTURE.md`と関連する`docs/contracts/`を読む。
3. Issueに記載された許可パス・禁止パスを確認する。
4. 同じ責務を持つ未完了PRがないことを確認する。
5. 専用ブランチを作成する。
6. 最初のコメントで、担当範囲と予定する公開面を宣言する。

## 4. 共有ファイル

以下は統合担当以外、原則変更禁止：

```text
settings.gradle*
build.gradle*
gradle.properties
gradle/libs.versions.toml
.github/workflows/**
.github/agent-scopes.json
ARCHITECTURE.md
docs/contracts/**
loader metadata files
main initialization / registry wiring
```

変更が必要な場合は、担当PRに混ぜず、理由・必要なシグネチャ・移行影響をIssueへ記載する。

## 5. API変更提案

公開契約の変更が必要な場合、最低限以下を提示する。

```text
Problem:
Proposed contract:
Alternatives considered:
Affected modules:
Migration cost:
Compatibility impact:
```

統合担当が契約を先に変更し、その変更を取り込んでから機能実装を続ける。

## 6. コミットとPR

コミットは担当責務だけを含める。PR本文には以下を含める。

- 変更内容
- 担当Issue
- 変更したパス
- 変更していない境界
- テスト方法
- 未実装・制約
- API変更の有無
- スクリーンショット（GUI変更時）

PRはDraftで作成する。merge、auto-merge、base変更は統合担当の許可なしに行わない。

## 7. 完了条件

- Issueの受け入れ条件を満たす
- 担当モジュールのテストを追加する
- 既存テストを壊さない
- 決定論的な出力を確認する
- Minecraft公式テクスチャ・モデル等を追加していない
- 公開契約を無断変更していない
- PR本文に既知の制約を記載する

## 8. 統合順序

推奨順：

1. API契約
2. Snapshot生成
3. メッシュ生成
4. マテリアル・マーカー
5. OBJ/glTFエクスポーター
6. GUI接続
7. Blenderアドオン・Live Link
8. 高度な最適化・双方向変換

下流担当は上流契約のPRを直接参照して独自コピーを作らない。必要なら契約PRをbaseにしたstacked PRとして作業する。

## 9. 競合の扱い

- 完成時に最新baseへrebaseする。
- コンフリクト解消で他担当の意味を推測しない。
- 共有契約の競合は統合担当へ戻す。
- 生成ファイルやフォーマット済み全体差分を避ける。
- 同じファイルを複数担当が触る必要が出たら、責務分割を見直す。

## 10. エージェント向けIssueテンプレート

```text
Responsibility:
Allowed paths:
Forbidden paths:
Depends on:
Stable contracts used:
Acceptance criteria:
Required tests:
Out of scope:
Base branch:
```

担当Issueにこの情報が不足している場合でも、`ARCHITECTURE.md`から明確に判断できる範囲は進め、曖昧な共有変更だけを避ける。
