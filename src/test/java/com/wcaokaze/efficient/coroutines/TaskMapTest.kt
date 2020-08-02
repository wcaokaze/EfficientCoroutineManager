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
import kotlinx.coroutines.sync.*
import java.util.*

@RunWith(JUnit4::class)
class TaskMapTest {
   @Test fun 重複なし_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         launch(taskMap = taskMap, taskId = 0) { results += 0 }
         launch(taskMap = taskMap, taskId = 1) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複なし_async_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap = taskMap, taskId = 0) { results += 0 }
         val deferred1 = async(taskMap = taskMap, taskId = 1) { results += 1 }
         joinAll(deferred0, deferred1)
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複なし_launchAsync_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val job      = launch(taskMap = taskMap, taskId = 0) { results += 0 }
         val deferred = async (taskMap = taskMap, taskId = 1) { results += 1 }
         joinAll(job, deferred)
      }

      assertEquals(listOf(0, 1), results)
   }

   // ==========================================================================

   @Test fun 重複あり_先にdispatchされた方のみ実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val dispatcher = DequeDispatcher(workerThreadCount = 1)
         val taskMap = TaskMap()

         launch(dispatcher.first) {
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(300L)
         }

         launch(dispatcher.first, taskMap = taskMap, taskId = 0) { results += 0; delay(50L) }
         launch(dispatcher.first, taskMap = taskMap, taskId = 0) { results += 1; delay(50L) }
         launch(dispatcher.last,  taskMap = taskMap, taskId = 1) { results += 2; delay(50L) }
         launch(dispatcher.last,  taskMap = taskMap, taskId = 1) { results += 3; delay(50L) }
      }

      assertEquals(listOf(1, 2), results)
   }

   @Test fun taskIdが重複しててもTaskMapが違ったら実行される() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap0 = TaskMap()
         val taskMap1 = TaskMap()

         launch(taskMap = taskMap0, taskId = 0) { results += 0; delay(50L) }
         launch(taskMap = taskMap1, taskId = 0) { results += 1; delay(50L) }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複あり_async() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap = taskMap, taskId = 0) { results += 0; delay(50L) }
         val deferred1 = async(taskMap = taskMap, taskId = 0) { results += 1; delay(50L) }
         joinAll(deferred0, deferred1)
      }

      assertEquals(listOf(0), results)
   }

   @Test fun 重複あり_async_二回目以降は一回目のDeferredをawaitする() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap = taskMap, taskId = 0) { delay(50L); Any() }
         val deferred1 = async(taskMap = taskMap, taskId = 0) { delay(50L); Any() }
         val deferred2 = async(taskMap = taskMap, taskId = 0) { delay(50L); Any() }

         val any0 = deferred0.await()
         val any1 = deferred1.await()
         val any2 = deferred2.await()

         assertSame(any0, any1)
         assertSame(any0, any2)
      }
   }

   @Test fun 重複_asyncのあとにlaunch_isActive() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred = async (taskMap = taskMap, taskId = 0) { delay(50L) }
         val job      = launch(taskMap = taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         job.join()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
      }
   }

   @Test fun 重複_launchのあとにasync_isActive() {
      runBlocking {
         val taskMap = TaskMap()

         val job      = launch(taskMap = taskMap, taskId = 0) { delay(50L) }
         val deferred = async (taskMap = taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         deferred.join()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
      }
   }

   @Test fun 重複_asyncのあとにlaunch_join() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred = async (taskMap = taskMap, taskId = 0) { delay(50L) }
         val job      = launch(taskMap = taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         job.join()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
      }
   }

   @Test fun 重複_launchのあとにasync_たまたまasyncの返り値もUnitの場合のawait() {
      runBlocking {
         val taskMap = TaskMap()

         val job      = launch(taskMap = taskMap, taskId = 0) { delay(50L) }
         val deferred = async (taskMap = taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         val unit = deferred.await()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
         assertSame(Unit, unit)
      }
   }

   @Test fun 重複_launchのあとにasync_awaitするとUnitが返ってきてClassCastExceptionになる() {
      runBlocking {
         val taskMap = TaskMap()

         launch(taskMap = taskMap, taskId = 0) { delay(50L) }
         val deferred = async(taskMap = taskMap, taskId = 0) { delay(50L); 3 }

         assertFailsWith<ClassCastException> {
            // コンパイラにキャストを挿入させるためにawaitの返り値を使う必要がある
            @Suppress("UNUSED_VARIABLE")
            val i = deferred.await()
         }
      }
   }

   @Test fun 重複_async同士でも型が違うとClassCastExceptionになるんだぜ() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap = taskMap, taskId = 0) { delay(50L); "" }
         val deferred1 = async(taskMap = taskMap, taskId = 0) { delay(50L); 3 }

         assertFailsWith<ClassCastException> {
            // コンパイラにキャストを挿入させるためにawaitの返り値を使う必要がある
            @Suppress("UNUSED_VARIABLE")
            val i = deferred1.await()
         }
      }
   }

   // ==========================================================================

   @Test fun 重複してるけど先に実行したタスクがすでに終わってる() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val job = launch(taskMap = taskMap, taskId = 0) { results += 0 }
         job.join()
         launch(taskMap = taskMap, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複してるけど先に実行したタスクがすでに終わってる_async() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap = taskMap, taskId = 0) { results += 0 }
         deferred0.join()
         val deferred1 = async(taskMap = taskMap, taskId = 0) { results += 1 }
         deferred1.join()
      }

      assertEquals(listOf(0, 1), results)
   }

   // ==========================================================================

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

   /*
    * L---D---F
    * L-D=====F
    */
   @Test fun launch_dispatch間に他のコルーチンがdispatch_join() {
      val results = LinkedList<Int>()

      test { dispatcher ->
         launch(dispatcher.first, taskId = 0) { results += 0; delay(50L) }
         launch(dispatcher.first, taskId = 0) { results += 1; delay(50L) }
      }

      assertEquals(listOf(1), results)
   }

   /*
    * L------DF
    * L-D==F
    */
   @Test fun launch_dispatch間に他のコルーチンがdispatchして終わる_即終了() {
      val results = LinkedList<Int>()

      test { dispatcher ->
         val mutex = Mutex(locked = true)

         launch(dispatcher.last) {
            mutex.unlock()

            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(150L)
         }

         launch(dispatcher.last,     taskId = 0) { results += 0 }
         mutex.lock()
         launch(Dispatchers.Default, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(1), results)
   }

   /*
    *     L--D--F
    * L-D=======F
    */
   @Test fun launchした時点ですでに他のコルーチンがdispatch済み_join() {
      val results = LinkedList<Int>()

      test { dispatcher ->
         val mutex = Mutex(locked = true)
         launch(dispatcher.last, taskId = 0) { mutex.unlock(); results += 0; delay(50L) }
         mutex.lock()
         launch(dispatcher.last, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(0), results)
   }

   /*
    *     L---DF
    * L-D===F
    */
   @Test fun launchした時点ですでに他のコルーチンがdispatch済みでこっちのdispatchまでに終わる_即終了() {
      val results = LinkedList<Int>()

      test { dispatcher ->
         val mutex = Mutex(locked = true)
         launch(dispatcher.last, taskId = 0) { mutex.unlock(); results += 0; delay(50L) }
         mutex.lock()

         launch(dispatcher.last) {
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(150L)
         }

         launch(dispatcher.last, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(0), results)
   }

   /*
    *        L-D==F
    * L-D==F
    */
   @Test fun launchした時点で他のコルーチンの実行が完全に終わってる_再度実行() {
      val results = LinkedList<Int>()

      test { dispatcher ->
         val mutex = Mutex(locked = true)
         launch(dispatcher.last, taskId = 0) { results += 0; delay(50L); mutex.unlock() }
         mutex.lock()
         launch(dispatcher.last, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }
}
