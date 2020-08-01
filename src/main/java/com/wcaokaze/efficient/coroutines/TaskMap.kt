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
import kotlin.coroutines.*

/**
 * 通常のlaunchと同様にコルーチンを起動しますが、
 * このコルーチンが起動されてから実際に処理が始まるまでの間に、
 * 同じtaskIdの他のコルーチンの処理がすでに始まってしまっていた場合、
 * このコルーチンの処理は行わず、先に始まっていたコルーチンに
 * [join][Job.join]します。
 *
 * 典型的にはGET系のネットワーク処理の重複を避けるために使いますが、taskIdとして
 * [java.net.URL]は使わないように注意してください。
 * [java.net.URLのequals](https://docs.oracle.com/javase/jp/8/docs/api/java/net/URL.html#equals-java.lang.Object-)
 * は、IPアドレスの解決を行うため、とんでもなく遅いです。
 */
fun CoroutineScope.launch(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskMap: TaskMap = GlobalTaskMap,
      taskId: Any,
      block: suspend CoroutineScope.() -> Unit
): Job {
   lateinit var job: Job

   job = launch(context, start) {
      val duplicatedTask = taskMap.setIfNotContained(taskId, job)

      if (duplicatedTask != null) {
         duplicatedTask.join()
      } else {
         block()

         synchronized (taskMap) {
            taskMap -= taskId
         }
      }
   }

   return job
}

/**
 * 通常のasyncと同様にコルーチンを起動しますが、
 * このコルーチンが起動されてから実際に処理が始まるまでの間に、
 * 同じtaskIdの他のコルーチンの処理がすでに始まってしまっていた場合、
 * このコルーチンの処理は行わず、先に始まっていたコルーチンを
 * [await][Deferred.await]します。
 *
 * 典型的にはGET系のネットワーク処理の重複を避けるために使いますが、taskIdとして
 * [java.net.URL]は使わないように注意してください。
 * [java.net.URLのequals](https://docs.oracle.com/javase/jp/8/docs/api/java/net/URL.html#equals-java.lang.Object-)
 * は、IPアドレスの解決を行うため、とんでもなく遅いです。
 *
 * また、全く違う処理に偶然同じtaskIdを割り振ることによって
 * ClassCastExceptionが発生し得ることにも注意してください。
 * ```kotlin
 * val deferredInt    = async(taskId = 0) { fetchInt() }
 * val deferredString = async(taskId = 0) { fetchString() }
 *
 * val string = deferredString.await() // ClassCastException
 * ```
 */
fun <T> CoroutineScope.async(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskMap: TaskMap = GlobalTaskMap,
      taskId: Any,
      block: suspend CoroutineScope.() -> T
): Deferred<T> {
   lateinit var deferred: Deferred<T>

   deferred = async(context, start) {
      val duplicatedTask = taskMap.setIfNotContained(taskId, deferred)

      when {
         duplicatedTask is Deferred<*> -> {
            @Suppress("UNCHECKED_CAST")
            duplicatedTask.await() as T
         }

         duplicatedTask != null -> {
            duplicatedTask.join()

            @Suppress("UNCHECKED_CAST")
            Unit as T
         }

         else -> {
            val r = block()

            synchronized (taskMap) {
               taskMap -= taskId
            }

            r
         }
      }
   }

   return deferred
}

/**
 * 指定したtaskIdのJobがすでにこのTaskMapに存在する場合それを返します。
 *
 * そうでなければこのTaskMapに指定したjobをセットし、nullを返します。
 */
private fun TaskMap.setIfNotContained(taskId: Any, job: Job): Job? {
   synchronized (this) {
      val task = this[taskId]
      if (task != null) { return task }
      this[taskId] = job
      return null
   }
}

@Suppress("FunctionName")
fun TaskMap(): TaskMap = TaskMapImpl()

/**
 * taskIdを管理するインスタンスです。概念的には `Map<Any, Job>` に近いです。
 */
interface TaskMap {
   operator fun get(taskId: Any): Job?
   operator fun set(taskId: Any, job: Job)
   operator fun minusAssign(taskId: Any)
}

object GlobalTaskMap : TaskMap {
   private val jobMap = HashMap<Any, Job>()

   override fun get(taskId: Any): Job? = jobMap[taskId]
   override fun set(taskId: Any, job: Job) { jobMap[taskId] = job }
   override fun minusAssign(taskId: Any) { jobMap -= taskId }
}

private class TaskMapImpl : TaskMap {
   private val jobMap = HashMap<Any, Job>()

   override fun get(taskId: Any): Job? = jobMap[taskId]
   override fun set(taskId: Any, job: Job) { jobMap[taskId] = job }
   override fun minusAssign(taskId: Any) { jobMap -= taskId }
}
