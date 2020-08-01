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
 * 指定したtaskIdのコルーチンがまだlaunchされていない場合、
 * もしくは過去にlaunchされたがすでに完了済みの場合、launchします。
 *
 * 指定したtaskIdのコルーチンがすでにlaunchされていて、実行中であるか、もしくは
 * いずれかのsuspendポイントで待機中、
 * もしくはまだ開始されていないが処理が予約されていて待機中である場合には
 * そのコルーチンのJobを返却します。
 *
 * 典型的にはGET系のネットワーク処理の重複を避けるために使いますが、taskIdとして
 * [java.net.URL]は使わないように注意してください。
 * [java.net.URLのequals](https://docs.oracle.com/javase/jp/8/docs/api/java/net/URL.html#equals-java.lang.Object-)
 * は、IPアドレスの解決を行うため、とんでもなく遅いです。
 *
 * @see TaskMap
 */
inline fun CoroutineScope.launch(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskId: Any,
      crossinline block: suspend CoroutineScope.() -> Unit
): Job {
   val taskMap = context[TaskMap] ?: GlobalTaskMap

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

/**
 * 指定したtaskIdのコルーチンがまだlaunchされていない場合、
 * もしくは過去にlaunchされたがすでに完了済みの場合、launchします。
 *
 * 指定したtaskIdのコルーチンがすでにlaunchされていて、実行中であるか、もしくは
 * いずれかのsuspendポイントで待機中、
 * もしくはまだ開始されていないが処理が予約されていて待機中である場合には
 * そのコルーチンのDeferredを返却します。
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
 *
 * @see TaskMap
 */
inline fun <T> CoroutineScope.async(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskId: Any,
      crossinline block: suspend CoroutineScope.() -> T
): Deferred<T> {
   val taskMap = context[TaskMap]!!

   synchronized (taskMap) {
      val job = taskMap[taskId]

      if (job is Deferred<*>) {
         @Suppress("UNCHECKED_CAST")
         return job as Deferred<T>
      }

      if (job != null) {
         val deferred = CompletableDeferred<Any?>()

         job.invokeOnCompletion { exception ->
            if (exception == null) {
               deferred.complete(Unit)
            } else {
               deferred.completeExceptionally(exception)
            }
         }

         @Suppress("UNCHECKED_CAST")
         return deferred as Deferred<T>
      }

      val deferred = async(context, start) {
         val r = block()
         taskMap -= taskId
         r
      }

      taskMap[taskId] = deferred
      return deferred
   }
}

@Suppress("FunctionName")
fun TaskMap(): TaskMap = TaskMapImpl()

/**
 * taskIdを管理するインスタンスです。概念的には `Map<Any, Job>` に近いです。
 *
 * launch、もしくはasyncにCoroutineContextとして渡すことで使用できます。
 *
 * ```kotlin
 * val taskMap = TaskMap()
 *
 * launch(taskMap, taskId = id) {
 * }
 * ```
 *
 * 指定しなかった場合[GlobalTaskMap]が使われます。
 */
interface TaskMap : CoroutineContext.Element {
   companion object Key : CoroutineContext.Key<TaskMap>

   operator fun get(taskId: Any): Job?
   operator fun set(taskId: Any, job: Job)
   operator fun minusAssign(taskId: Any)
}

object GlobalTaskMap : TaskMap {
   private val jobMap = HashMap<Any, Job>()

   override val key get() = TaskMap

   override fun get(taskId: Any): Job? = jobMap[taskId]
   override fun set(taskId: Any, job: Job) { jobMap[taskId] = job }
   override fun minusAssign(taskId: Any) { jobMap -= taskId }
}

private class TaskMapImpl : TaskMap {
   private val jobMap = HashMap<Any, Job>()

   override val key get() = TaskMap

   override fun get(taskId: Any): Job? = jobMap[taskId]
   override fun set(taskId: Any, job: Job) { jobMap[taskId] = job }
   override fun minusAssign(taskId: Any) { jobMap -= taskId }
}
