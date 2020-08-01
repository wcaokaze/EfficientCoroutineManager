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

import kotlinx.coroutines.*
import kotlin.concurrent.*
import kotlin.coroutines.*

abstract class PriorityDispatcher(workerThreadCount: Int = 3) {
   private val priorityDeque = PriorityDeque()

   init {
      repeat (workerThreadCount) {
         thread {
            while (true) {
               try {
                  // clear interruption status
                  Thread.interrupted()

                  priorityDeque.takeNextTask().run()
               } catch (e: Exception) {
                  // ignore
               }
            }
         }
      }
   }

   protected fun addNewDeque(): DequeDispatcher {
      val deque = priorityDeque.addNewDeque()
      return PriorityDequeDispatcher(deque)
   }

   private inner class PriorityDequeDispatcher(
         private val taskDeque: PriorityDeque.TaskDeque
   ) : DequeDispatcher {
      override val first = object : CoroutineDispatcher() {
         override fun dispatch(context: CoroutineContext, block: Runnable) {
            taskDeque.addFirst(block)
         }
      }

      override val last = object : CoroutineDispatcher() {
         override fun dispatch(context: CoroutineContext, block: Runnable) {
            taskDeque.addLast(block)
         }
      }
   }
}
