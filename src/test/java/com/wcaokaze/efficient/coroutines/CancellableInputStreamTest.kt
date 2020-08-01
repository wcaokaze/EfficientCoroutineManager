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

package com.wcaokaze.efficient.coroutines

import org.junit.runner.*
import org.junit.runners.*
import kotlin.test.*

import kotlinx.coroutines.*

@RunWith(JUnit4::class)
class CancellableInputStreamTest {
   @Test fun キャンセル() {
      runBlocking {
         var readCount = 0

         val job = launch(Dispatchers.IO) {
            ByteArray(32)
                  .inputStream()
                  .cancellable(coroutineContext)
                  .use { inputStream ->
                     while (true) {
                        inputStream.read()
                        readCount++
                        Thread.sleep(50L)
                     }
                  }
         }

         delay(50L)
         job.cancelAndJoin()
         assertTrue(readCount < 32)
      }
   }

   @Test fun バッファリングされた後だとキャンセルできませんね() {
      runBlocking {
         var readCount = 0

         val job = launch(Dispatchers.IO) {
            ByteArray(32)
                  .inputStream()
                  .cancellable(coroutineContext)
                  .buffered()
                  .use { inputStream ->
                     while (true) {
                        inputStream.read()
                        readCount++
                        Thread.sleep(50L)
                     }
                  }
         }

         delay(50L)
         job.cancelAndJoin()
         assertEquals(32, readCount)
      }
   }

   @Test fun バッファサイズよりクソデカなデータを読み込んでいればキャンセルできることもある() {
      runBlocking {
         var readCount = 0

         val job = launch(Dispatchers.IO) {
            ByteArray(32)
                  .inputStream()
                  .cancellable(coroutineContext)
                  .buffered(bufferSize = 8)
                  .use { inputStream ->
                     while (true) {
                        inputStream.read()
                        readCount++
                        Thread.sleep(50L)
                     }
                  }
         }

         delay(50L)
         job.cancelAndJoin()
         assertTrue(readCount < 32)
      }
   }
}
