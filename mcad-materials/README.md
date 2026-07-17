# mcad-materials

MinecraftやLoaderに依存しない中立マテリアルモジュールです。

- m+CAD独自の単色・PBR定義
- canonical block IDからの決定的な識別色
- 頂点カラー用データ
- schema 1.0のユーザーマッピングJSON
- project-relativeなユーザー素材参照の明示的検証

内蔵色はMinecraftテクスチャから抽出・平均化したものではありません。通常の解決処理はファイルシステムへアクセスせず、ユーザー素材は`ExternalAssetValidator`を明示的に呼び出した場合だけ検証されます。検証はコピーやアップロードを行いません。
