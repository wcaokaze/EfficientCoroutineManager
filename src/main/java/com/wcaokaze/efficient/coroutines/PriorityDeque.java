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

package com.wcaokaze.efficient.coroutines;

import java.util.*;

/* package */ final class PriorityDeque {
   private final List<TaskDeque> mTaskDequeList = new LinkedList<TaskDeque>();

   public final synchronized TaskDeque addNewDeque() {
      final TaskDeque taskDeque = new TaskDeque();
      mTaskDequeList.add(taskDeque);
      return taskDeque;
   }

   public final synchronized Runnable takeNextTask() throws InterruptedException {
      while (true) {
         for (final TaskDeque taskDeque : mTaskDequeList) {
            final Runnable task = taskDeque.pollFirst();
            if (task != null) { return task; }
         }

         wait();
      }
   }

   /* package */ final class TaskDeque {
      private final Deque<Runnable> mDeque = new LinkedList<Runnable>();

      public final void addFirst(final Runnable task) {
         synchronized (PriorityDeque.this) {
            mDeque.addFirst(task);
            PriorityDeque.this.notifyAll();
         }
      }

      public final void addLast(final Runnable task) {
         synchronized (PriorityDeque.this) {
            mDeque.addLast(task);
            PriorityDeque.this.notifyAll();
         }
      }

      /* package */ final Runnable pollFirst() {
         return mDeque.pollFirst();
      }
   }
}
