# m+CAD Blender Live Link add-on

## Install

1. GitHub Actions artifactまたはReleaseから`m-plus-cad-blender-addon.zip`を取得します。
2. Blenderで`編集 > プリファレンス > アドオン > ディスクからインストール`を開きます。
3. ZIPを選択し、`m+CAD Live Link`を有効化します。
4. 3D ViewのSidebarに`m+CAD`tabが追加されます。

## Connect

1. Minecraftで選択の始点と終点を設定します。
2. `L`またはm+CAD設定画面からLive Linkを開始します。
3. Minecraft画面に表示されたportと32桁session tokenをBlender panelへ入力します。
4. `接続`を押します。
5. 初回full sceneが入り、その後は約1秒間隔で変更要素だけが同期されます。

`I`を押すと即時再同期を予約します。接続が切れた場合、add-onは同じtoken/portで自動再接続します。Live LinkをMinecraft側で再起動するとtokenが変わるため、Blender側も新tokenへ更新してください。

## Ownership

add-onは`m+CAD Live` Collectionと`mcad_live_link`tagが付いたDataBlockだけを更新・削除します。それ以外のユーザーObject、Material、Collectionには触れません。

現在はMinecraft→Blenderの一方向同期です。BlenderからMinecraft worldへblockを配置・削除する機能はありません。
