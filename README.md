
EfficientCoroutines
================================================================================

コルーチンを効率よく使うためのいろいろ


CancellableInputStream
--------------------------------------------------------------------------------

コルーチンがキャンセルされた際に処理を中断できるInputStream。
```kotlin
val text = openInputStream().cancellable(coroutineContext).bufferedReader().use { reader ->
   reader.readText()
}
```
画像とかデカすぎるデータをダウンロードするときに時間がかかると
ユーザーは戻るボタンとか押しちゃうんですけど、
普通に実装した場合バックグラウンドでダウンロード処理は続いてますから
そのあとのアプリの通信処理が全体的に遅くなるんですね。

cancellableという一文を加えておけば、
CoroutineScopeという仕組みがあるので
戻るボタンを押すと自動的にコルーチンはキャンセルされて
ダウンロード処理が中断されるって感じですよね。


DequeDispatcher
--------------------------------------------------------------------------------

Dequeってご存知ですか？  
日本語では両端キューとか呼ばれる、
前からでも後ろからでもデータを追加できる構造なんですね。

普通のDispatcherはlaunchした順に実行されますけど
両端キューを使うことで多少は柔軟にしようというアイデアなんですねこれは。
```kotlin
val dispatcher = DequeDispatcher()

launch(dispatcher.first) {
}
launch(dispatcher.last) {
}
```


PriorityDispatcher
--------------------------------------------------------------------------------

複数のDequeDispatcherを統合したDispatcherです。

`addNewDeque` でDequeDispatcherを作れるので以下のようにしていきます。
```kotlin
object NetworkDispatcher : PriorityDispatcher() {
   val critical   = addNewDeque()
   val background = addNewDeque()
}

launch(NetworkDispatcher.critical.last) {
   // 0
}
launch(NetworkDispatcher.background.last) {
   // 1
}
launch(NetworkDispatcher.background.first) {
   // 2
}
```
以下のような感じになるので先頭から順に実行されていくイメージです。
```
[         // NetworkDispatcher
   [0],   // critical
   [2, 1] // background
]
```

たとえば画像のダウンロード処理なんかはとにかく時間がかかるので、
他にタスクのない余裕のあるときにあらかじめバックグラウンドでダウンロードしつつ、
ユーザーが画像をタップしてきて拡大表示しないといけなくなったら
バックグラウンドではなく優先してダウンロードする。
そういうシチュエーションがあるはずなんですけど、PriorityDispatcherがあれば
とても簡単に実現できますよね。


インストール
--------------------------------------------------------------------------------

Gradle
```groovy
repositories {
   maven { url 'https://dl.bintray.com/wcaokaze/maven' }
}

dependencies {
   implementation 'com.wcaokaze.efficientcoroutines:efficientcoroutines:0.0.0'
}
```

Gradle (Kotlin)
```kotlin
repositories {
   maven(url = "https://dl.bintray.com/wcaokaze/maven")
}

dependencies {
   implementation("com.wcaokaze.efficientcoroutines:efficientcoroutines:0.0.0")
}
```


LICENSE
--------------------------------------------------------------------------------

[Apache License 2.0](LICENSE)

