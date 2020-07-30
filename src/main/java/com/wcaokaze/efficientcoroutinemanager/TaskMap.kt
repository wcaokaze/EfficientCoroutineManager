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

import kotlinx.coroutines.*
import kotlin.coroutines.*

inline fun CoroutineScope.launch(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskId: Any,
      crossinline block: suspend CoroutineScope.() -> Unit
): Job {
   val taskMap = context[TaskMap]!!

   synchronized (taskMap) {
      var job = taskMap[taskId]
      if (job != null) { return job }

      job = launch(context, start) {
         block()
         taskMap -= taskId
      }

      taskMap[taskId] = job
      return job
   }
}

@Suppress("FunctionName")
fun TaskMap(): TaskMap = TaskMapImpl()

interface TaskMap : CoroutineContext.Element {
   companion object Key : CoroutineContext.Key<TaskMap>

   operator fun get(taskId: Any): Job?
   operator fun set(taskId: Any, job: Job)
   operator fun minusAssign(taskId: Any)
}

private class TaskMapImpl : TaskMap {
   private val jobMap = HashMap<Any, Job>()

   override val key get() = TaskMap

   override fun get(taskId: Any): Job? = jobMap[taskId]
   override fun set(taskId: Any, job: Job) { jobMap[taskId] = job }
   override fun minusAssign(taskId: Any) { jobMap -= taskId }
}
