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
class PriorityDispatcherTest {
   private class PriorityDispatcherImpl(workerThreadCount: Int)
         : PriorityDispatcher(workerThreadCount)
   {
      val dispatcher1 = addNewDeque()
      val dispatcher2 = addNewDeque()
      val dispatcher3 = addNewDeque()
   }

   private inline fun test(
         crossinline action: suspend CoroutineScope.(PriorityDispatcherImpl) -> Unit
   ) {
      val priorityDispatcher = PriorityDispatcherImpl(workerThreadCount = 1)

      runBlocking {
         // action内でlaunchしたコルーチンが即発火されないように
         // 300ミリ秒間WorkerThreadを占有する
         launch(priorityDispatcher.dispatcher1.first) {
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(300L)
         }

         action(priorityDispatcher)
      }
   }

   @Test fun dispatcher1のみ_lastのみ() {
      val results = LinkedList<Int>()

      test { priorityDispatcher ->
         launch(priorityDispatcher.dispatcher1.last) { results += 0 }
         launch(priorityDispatcher.dispatcher1.last) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun dispatcher2のみ_lastのみ() {
      val results = LinkedList<Int>()

      test { priorityDispatcher ->
         launch(priorityDispatcher.dispatcher2.last) { results += 0 }
         launch(priorityDispatcher.dispatcher2.last) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun dispatcher1のみ_firstとlast混在() {
      val results = LinkedList<Int>()

      test { priorityDispatcher ->
         launch(priorityDispatcher.dispatcher1.first) { results += 0 }
         launch(priorityDispatcher.dispatcher1.last)  { results += 1 }
         launch(priorityDispatcher.dispatcher1.first) { results += 2 }
         launch(priorityDispatcher.dispatcher1.last)  { results += 3 }
      }

      assertEquals(listOf(2, 0, 1, 3), results)
   }

   @Test fun dispatcher1と2混在_lastのみ() {
      val results = LinkedList<Int>()

      test { priorityDispatcher ->
         launch(priorityDispatcher.dispatcher1.last) { results += 0 }
         launch(priorityDispatcher.dispatcher2.last) { results += 1 }
         launch(priorityDispatcher.dispatcher1.last) { results += 2 }
         launch(priorityDispatcher.dispatcher2.last) { results += 3 }
      }

      assertEquals(listOf(0, 2, 1, 3), results)
   }

   @Test fun dispatcher1から3混在_firstとlastも混在() {
      val results = LinkedList<Int>()

      test { priorityDispatcher ->
         launch(priorityDispatcher.dispatcher1.first) { results +=  0 }
         launch(priorityDispatcher.dispatcher1.last)  { results +=  1 }
         launch(priorityDispatcher.dispatcher2.first) { results +=  2 }
         launch(priorityDispatcher.dispatcher2.last)  { results +=  3 }
         launch(priorityDispatcher.dispatcher3.first) { results +=  4 }
         launch(priorityDispatcher.dispatcher3.last)  { results +=  5 }
         launch(priorityDispatcher.dispatcher1.first) { results +=  6 }
         launch(priorityDispatcher.dispatcher1.last)  { results +=  7 }
         launch(priorityDispatcher.dispatcher2.first) { results +=  8 }
         launch(priorityDispatcher.dispatcher2.last)  { results +=  9 }
         launch(priorityDispatcher.dispatcher3.first) { results += 10 }
         launch(priorityDispatcher.dispatcher3.last)  { results += 11 }
      }

      assertEquals(
            listOf(6, 0, 1, 7, 8, 2, 3, 9, 10, 4, 5, 11),
            results
      )
   }

   @Suppress("BlockingMethodInNonBlockingContext")
   @Test fun workerThread() {
      val priorityDispatcher = PriorityDispatcherImpl(workerThreadCount = 5)

      fun test() {
         val startTime = System.currentTimeMillis()
         val dispatchedTime = Collections.synchronizedList(LinkedList<Long>())

         runBlocking {
            launch(priorityDispatcher.dispatcher3.last)  { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
            launch(priorityDispatcher.dispatcher3.first) { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
            launch(priorityDispatcher.dispatcher2.last)  { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
            launch(priorityDispatcher.dispatcher2.first) { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
            launch(priorityDispatcher.dispatcher1.last)  { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
            launch(priorityDispatcher.dispatcher1.first) { dispatchedTime += System.currentTimeMillis() - startTime; Thread.sleep(150L) }
         }

         for (t in dispatchedTime.take(5)) {
            assertTrue(t < 150L)
         }

         assertTrue(dispatchedTime[5] >= 150L)
      }

      test()
      Thread.sleep(300L) // 一旦タスクを空にしてworkerThreadをwaitに入れさせる
      test()
   }
}
