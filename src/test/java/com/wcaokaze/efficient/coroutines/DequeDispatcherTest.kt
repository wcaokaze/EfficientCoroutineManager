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
import java.util.*

@RunWith(JUnit4::class)
class DequeDispatcherTest {
   private inline fun test(crossinline action: suspend CoroutineScope.(DequeDispatcher) -> Unit) {
      val dequeDispatcher = DequeDispatcher(workerThreadCount = 1)

      runBlocking {
         // action内でlaunchしたコルーチンが即発火されないように
         // 300ミリ秒間WorkerThreadを占有する
         launch(dequeDispatcher.first) {
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(300L)
         }

         action(dequeDispatcher)
      }
   }

   @Test fun lastのみ_launchした順に実行される() {
      val results = LinkedList<Int>()

      test { dequeDispatcher ->
         launch(dequeDispatcher.last) { results += 0 }
         launch(dequeDispatcher.last) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun firstのみ_launchした順と逆に実行される() {
      val results = LinkedList<Int>()

      test { dequeDispatcher ->
         launch(dequeDispatcher.first) { results += 0 }
         launch(dequeDispatcher.first) { results += 1 }
      }

      assertEquals(listOf(1, 0), results)
   }

   @Test fun firstLast混在_first優先() {
      val results = LinkedList<Int>()

      test { dequeDispatcher ->
         launch(dequeDispatcher.first) { results += 0 }
         launch(dequeDispatcher.last)  { results += 1 }
         launch(dequeDispatcher.first) { results += 2 }
         launch(dequeDispatcher.last)  { results += 3 }
      }

      assertEquals(listOf(2, 0, 1, 3), results)
   }

   @Test fun cancel() {
      test { dequeDispatcher ->
         val job = launch(dequeDispatcher.first) {
            delay(300L)
            fail("キャンセルされてませんよ")
         }

         launch(dequeDispatcher.first) {
            job.cancel()
         }
      }
   }

   @Test fun 例外処理() {
      val dequeDispatcher = DequeDispatcher(workerThreadCount = 1)

      val deferred = GlobalScope.async<Int>(dequeDispatcher.first) {
         throw Exception("例外です")
      }

      runBlocking {
         val exception = assertFails {
            deferred.await()
         }

         assertEquals(Exception::class, exception::class)
         assertEquals("例外です", exception.message)
      }
   }
}
