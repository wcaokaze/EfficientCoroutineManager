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

import java.util.*;
import java.util.concurrent.*;

/* package */ abstract class DequeExecutorService {

   // ==== Request =============================================================

   /* package */ abstract class Request<V> implements Future<V> {
      private volatile boolean mIsDone = false;
      private volatile boolean mIsCancelled = false;

      // Nullable
      private Thread mWorkerThread = null;

      // Nullable
      private V mResult = null;

      // Nullable
      private Throwable mFailureCause = null;

      /* package */ Request() {
         mRequests.add(this);
      }

      public abstract void invoke(Thread workerThread) throws Exception;

      @Override
      public final boolean isCancelled() {
         return mIsCancelled;
      }

      @Override
      public final boolean isDone() {
         return mIsDone || mIsCancelled;
      }

      @Override
      public final synchronized boolean cancel(final boolean mayInterruptIfRunning) {
         if (mIsDone) { return false; }

         mIsCancelled = true;

         mRequests.remove(this);

         if (mayInterruptIfRunning) {
            if (mWorkerThread != null) {
               mWorkerThread.interrupt();
               mWorkerThread = null;
            }
         }

         synchronized (this) {
            notifyAll();
         }

         return true;
      }

      @Override
      public final synchronized V get() throws ExecutionException, InterruptedException {
         while (!mIsDone) {
            if (mIsCancelled) { throw new CancellationException(); }

            wait();
         }

         if (mFailureCause != null) {
            throw new ExecutionException(mFailureCause);
         }

         return mResult;
      }

      @Override
      public final synchronized V get(final long timeout, final TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException
      {
         final long timeoutMillis = unit.toMillis(timeout);
         final long abortTime = System.currentTimeMillis() + timeoutMillis;

         while (!mIsDone) {
            if (System.currentTimeMillis() > abortTime) { throw new TimeoutException(); }
            if (mIsCancelled) { throw new CancellationException(); }

            wait(timeoutMillis);
         }

         if (mFailureCause != null) {
            throw new ExecutionException(mFailureCause);
         }

         return mResult;
      }

      /* package */ final synchronized void onActive(final Thread workerThread) {
         mWorkerThread = workerThread;
      }

      /* package */ final synchronized void onDone(final V result) {
         mWorkerThread = null;
         mIsDone = true;
         mResult = result;
         mRequests.remove(this);
         notifyAll();
      }

      /* package */ final synchronized void onFail(final Throwable cause) {
         mWorkerThread = null;
         mIsDone = true;
         mFailureCause = cause;
         mRequests.remove(this);
         notifyAll();
      }
   }

   private final class RunnableRequest extends Request<Void> {
      private final Runnable mRunnable;

      /* package */ RunnableRequest(final Runnable runnable) {
         mRunnable = runnable;
      }

      @Override
      public final void invoke(final Thread workerThread) {
         if (isCancelled()) { return; }

         onActive(workerThread);

         try {
            mRunnable.run();
            onDone(null);
         } catch (final Throwable t) {
            onFail(t);
         }
      }
   }

   // ==== RequestChannel ======================================================

   /* package */ interface RequestChannel {
      public boolean isShutdown();

      public Request<?> take() throws InterruptedException;
   }

   // ==== WorkerThread ========================================================

   /* package */ static final class WorkerThread extends Thread {
      private final RequestChannel mChannel;

      /* package */ WorkerThread(final RequestChannel channel) {
         mChannel = channel;
      }

      @Override
      public void run() {
         while (!mChannel.isShutdown()) {
            try {
               // clear interruption status
               Thread.interrupted();

               mChannel.take().invoke(this);
            } catch (final Exception e) {
               // ignore
            }
         }
      }
   }

   // ==========================================================================

   private final RequestChannel mChannel;

   private final List<Request<?>> mRequests
         = Collections.synchronizedList(new ArrayList<Request<?>>());

   protected DequeExecutorService(final RequestChannel channel) {
      mChannel = channel;
   }

   protected abstract void enqueueRequest(final Request<?> request);

   public final void execute(final Runnable command) {
      if (mChannel.isShutdown()) { throw new RejectedExecutionException(); }

      final RunnableRequest request = new RunnableRequest(command);
      enqueueRequest(request);
   }
}
