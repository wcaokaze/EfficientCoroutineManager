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

package com.wcaokaze.efficientcoroutinemanager;

/* package */ abstract class DequeExecutorService {

   // ==== Request =============================================================

   /* package */ abstract class Request {
      public abstract void invoke() throws Exception;
   }

   private final class RunnableRequest extends Request {
      private final Runnable mRunnable;

      /* package */ RunnableRequest(final Runnable runnable) {
         mRunnable = runnable;
      }

      @Override
      public final void invoke() {
         try {
            mRunnable.run();
         } catch (final Throwable t) {
            // ignore
         }
      }
   }

   // ==== RequestChannel ======================================================

   /* package */ interface RequestChannel {
      public Request take() throws InterruptedException;
   }

   // ==== WorkerThread ========================================================

   /* package */ static final class WorkerThread extends Thread {
      private final RequestChannel mChannel;

      /* package */ WorkerThread(final RequestChannel channel) {
         mChannel = channel;
      }

      @Override
      public void run() {
         while (true) {
            try {
               // clear interruption status
               Thread.interrupted();

               mChannel.take().invoke();
            } catch (final Exception e) {
               // ignore
            }
         }
      }
   }

   // ==========================================================================

   protected abstract void enqueueRequest(final Request request);

   public final void execute(final Runnable command) {
      final RunnableRequest request = new RunnableRequest(command);
      enqueueRequest(request);
   }
}
