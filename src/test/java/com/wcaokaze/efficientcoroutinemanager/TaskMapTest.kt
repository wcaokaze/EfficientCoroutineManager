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

import org.junit.runner.*
import org.junit.runners.*
import kotlin.test.*

import kotlinx.coroutines.*
import java.util.*

@RunWith(JUnit4::class)
class TaskMapTest {
   @Test fun 重複なし_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         launch(taskMap, taskId = 0) { results += 0 }
         launch(taskMap, taskId = 1) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複なし_async_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap, taskId = 0) { results += 0 }
         val deferred1 = async(taskMap, taskId = 1) { results += 1 }
         joinAll(deferred0, deferred1)
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複なし_launchAsync_すべて実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val job      = launch(taskMap, taskId = 0) { results += 0 }
         val deferred = async (taskMap, taskId = 1) { results += 1 }
         joinAll(job, deferred)
      }

      assertEquals(listOf(0, 1), results)
   }

   // ==========================================================================

   @Test fun 重複あり_先にlaunchした方のみ実行() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         launch(taskMap, taskId = 0) { results += 0; delay(50L) }
         launch(taskMap, taskId = 0) { results += 1; delay(50L) }
      }

      assertEquals(listOf(0), results)
   }

   @Test fun 重複あり_async() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap, taskId = 0) { results += 0; delay(50L) }
         val deferred1 = async(taskMap, taskId = 0) { results += 1; delay(50L) }
         joinAll(deferred0, deferred1)
      }

      assertEquals(listOf(0), results)
   }

   @Test fun 重複あり_二回目以降は一回目のJobを返す() {
      runBlocking {
         val taskMap = TaskMap()

         val job0 = launch(taskMap, taskId = 0) { delay(50L) }
         val job1 = launch(taskMap, taskId = 0) { delay(50L) }
         val job2 = launch(taskMap, taskId = 0) { delay(50L) }

         assertSame(job0, job1)
         assertSame(job0, job2)
      }
   }

   @Test fun 重複あり_async_二回目以降は一回目のDeferredを返す() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap, taskId = 0) { delay(50L) }
         val deferred1 = async(taskMap, taskId = 0) { delay(50L) }
         val deferred2 = async(taskMap, taskId = 0) { delay(50L) }

         assertSame(deferred0, deferred1)
         assertSame(deferred0, deferred2)
      }
   }

   @Test fun 重複_asyncのあとにlaunch_isActive() {
      runBlocking {
         val taskMap = TaskMap()

         val deferred = async (taskMap, taskId = 0) { delay(50L) }
         val job      = launch(taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         deferred.join()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
      }
   }

   @Test fun 重複_launchのあとにasync_isActive() {
      runBlocking {
         val taskMap = TaskMap()

         val job      = launch(taskMap, taskId = 0) { delay(50L) }
         val deferred = async (taskMap, taskId = 0) { delay(50L) }

         assertTrue(deferred.isActive)
         assertTrue(job.isActive)
         deferred.join()
         assertFalse(deferred.isActive)
         assertFalse(job.isActive)
      }
   }

   // ==========================================================================

   @Test fun 重複してるけど先に実行したタスクがすでに終わってる() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val job = launch(taskMap, taskId = 0) { results += 0 }
         job.join()
         launch(taskMap, taskId = 0) { results += 1 }
      }

      assertEquals(listOf(0, 1), results)
   }

   @Test fun 重複してるけど先に実行したタスクがすでに終わってる_async() {
      val results = LinkedList<Int>()

      runBlocking {
         val taskMap = TaskMap()

         val deferred0 = async(taskMap, taskId = 0) { results += 0 }
         deferred0.join()
         val deferred1 = async(taskMap, taskId = 0) { results += 1 }
         deferred1.join()
      }

      assertEquals(listOf(0, 1), results)
   }
}
