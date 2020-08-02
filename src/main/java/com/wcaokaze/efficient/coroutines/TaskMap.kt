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
import java.util.*
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
 *
 * ## タイミング詳細
 * launch呼び出しのタイミングを *L* 、
 * 実際にコルーチンの実行が開始される(≒最初のdispatch)タイミングを *D* 、
 * 処理が終わるタイミングを *F* とすると
 *
 * ### 処理が省略されるパターン
 * L〜Dの間に他のコルーチンのD〜Fが実行された場合です。
 * ```
 * L---------D------F
 *    L--D==========F
 * ```
 * ```
 * L---------------DF
 *    L--D======F
 * ```
 * ```
 *      L-------D------F
 * L--------D==========F
 * ```
 * ```
 *    L---------DF
 * L----D=====F
 * ```
 * ```
 *      L-------D---F
 * L--D=============F
 * ```
 * ```
 *      L-------DF
 * L--D=====F
 * ```
 *
 * ### 通常通り処理されるパターン
 * 同じtaskIdのコルーチンが他に存在しない場合か、
 * 他のコルーチンのDより先にこちらのDが発生した場合か、
 * こちらのLの時点ですでに他のコルーチンのFが終わっている場合です。
 * ```
 * L--------D==========F
 *      L-------D------F
 * ```
 * ```
 *    L--D==========F
 * L---------D------F
 * ```
 * ```
 *           L--D====F
 * L--D====F
 * ```
 */
fun CoroutineScope.launch(
      context: CoroutineContext = EmptyCoroutineContext,
      start: CoroutineStart = CoroutineStart.DEFAULT,
      taskMap: TaskMap = globalTaskMap,
      taskId: Any,
      block: suspend CoroutineScope.() -> Unit
): Job {
   lateinit var job: Job

   val launchedTime = System.currentTimeMillis()

   job = launch(context, start) {
      val activeTask = taskMap.onTaskActive(taskId, job, launchedTime)

      if (activeTask != null) {
         activeTask.join()
      } else {
         block()
      }

      taskMap.onTaskFinished(taskId, job)
   }

   taskMap.addScheduledJob(taskId, job)

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
      taskMap: TaskMap = globalTaskMap,
      taskId: Any,
      block: suspend CoroutineScope.() -> T
): Deferred<T> {
   lateinit var deferred: Deferred<T>

   val launchedTime = System.currentTimeMillis()

   deferred = async(context, start) {
      val activeTask = taskMap.onTaskActive(taskId, deferred, launchedTime)

      val r = when {
         activeTask is Deferred<*> -> {
            @Suppress("UNCHECKED_CAST")
            activeTask.await() as T
         }

         activeTask != null -> {
            activeTask.join()

            @Suppress("UNCHECKED_CAST")
            Unit as T
         }

         else -> {
            block()
         }
      }

      taskMap.onTaskFinished(taskId, deferred)

      r
   }

   taskMap.addScheduledJob(taskId, deferred)

   return deferred
}

val globalTaskMap = TaskMap()

/**
 * taskIdを管理するインスタンスです。概念的には `Map<Any, Job>` に近いです。
 */
class TaskMap {
   private val tasks = HashMap<Any, Task>()

   @Synchronized
   internal fun addScheduledJob(taskId: Any, job: Job) {
      var task = tasks[taskId]

      if (task == null) {
         task = Task(Task.State.Scheduled(System.currentTimeMillis()))
         tasks[taskId] = task
      }

      task.jobs += job
   }

   /**
    * @return すでにActiveなタスクが存在する場合そのJob
    */
   @Synchronized
   internal fun onTaskActive(taskId: Any, job: Job, launchedTime: Long): Job? {
      val task = tasks[taskId] ?: return null

      val state = task.state

      return when {
         state is Task.State.Active
               -> state.job

         state is Task.State.Finished && task.state.time > launchedTime
               -> state.job

         else -> {
            task.state = Task.State.Active(System.currentTimeMillis(), job)
            null
         }
      }
   }

   @Synchronized
   internal fun onTaskFinished(taskId: Any, job: Job) {
      val task = tasks[taskId] ?: return

      task.jobs -= job

      if (task.jobs.isEmpty()) {
         tasks -= taskId
         return
      }

      if ((task.state as? Task.State.Active)?.job == job) {
         task.state = Task.State.Finished(System.currentTimeMillis(), job)
      }
   }
}

internal class Task(var state: State) {
   val jobs: MutableList<Job> = LinkedList()

   sealed class State(val time: Long) {
      /** launchされたがまだ実行されていない */
      class Scheduled(time: Long) : State(time)

      /** 実行中。suspendポイントで待機中の状態も含む */
      class Active(time: Long, val job: Job) : State(time)

      /** 完了済み */
      class Finished(time: Long, val job: Job) : State(time)
   }
}
