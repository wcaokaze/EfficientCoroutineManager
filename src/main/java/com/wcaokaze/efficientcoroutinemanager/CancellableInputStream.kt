/*
 * Copyright 2020 wcaokaze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wcaokaze.efficientcoroutinemanager

import java.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * キャンセル可能なInputStreamです。
 *
 * [read][InputStream.read]呼び出し時、指定したcoroutineContextをチェックして、
 * コルーチンがキャンセルされている場合[CancellationException]をスローします。
 *
 * オーバーヘッド削減のため、このInputStreamをバッファリングすることが推奨されます。
 * ```kotlin
 * openInputStream().cancellable(coroutineContext).buffered().use { inputStream ->
 * }
 * ```
 */
fun InputStream.cancellable(coroutineContext: CoroutineContext)
      = CancellableInputStream(coroutineContext, this)

class CancellableInputStream(
      private val coroutineContext: CoroutineContext,
      private val wrapped: InputStream
) : InputStream() {
   constructor(coroutineScope: CoroutineScope, wrapped: InputStream)
         : this(coroutineScope.coroutineContext, wrapped)

   override fun read(): Int {
      if (!coroutineContext.isActive) { throw CancellationException() }
      return wrapped.read()
   }

   override fun read(b: ByteArray?): Int {
      if (!coroutineContext.isActive) { throw CancellationException() }
      return wrapped.read(b)
   }

   override fun read(b: ByteArray?, off: Int, len: Int): Int {
      if (!coroutineContext.isActive) { throw CancellationException() }
      return wrapped.read(b, off, len)
   }

   override fun available() = wrapped.available()
   override fun close() { wrapped.close() }
   override fun markSupported() = wrapped.markSupported()
   override fun mark(readlimit: Int) { wrapped.mark(readlimit) }
   override fun reset() { wrapped.reset() }
   override fun skip(n: Long) = wrapped.skip(n)
   override fun equals(other: Any?) = other is CancellableInputStream && other.wrapped == wrapped
   override fun hashCode() = wrapped.hashCode()
   override fun toString() = "CancellableInputStream($wrapped)"
}
