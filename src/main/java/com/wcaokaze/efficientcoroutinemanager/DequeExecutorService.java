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

/* package */ abstract class DequeExecutorService implements ExecutorService {

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

      protected abstract Runnable asRunnable();

      @Override
      public final boolean isCancelled() {
         return mIsCancelled;
      }

      @Override
      public final boolean isDone() {
         return mIsDone || mIsCancelled;
      }

      /* package */ final boolean isActive() {
         return mWorkerThread != null;
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

      @Override
      protected final Runnable asRunnable() {
         return mRunnable;
      }
   }

   private final class CallableRequest<V> extends Request<V> {
      private final Callable<V> mCallable;

      /* package */ CallableRequest(final Callable<V> callable) {
         mCallable = callable;
      }

      @Override
      public final void invoke(final Thread workerThread) {
         if (isCancelled()) { return; }

         onActive(workerThread);

         try {
            final V result = mCallable.call();
            onDone(result);
         } catch (final Throwable t) {
            onFail(t);
         }
      }

      @Override
      protected final Runnable asRunnable() {
         return new Runnable() {
            @Override
            public final void run() {
               try {
                  mCallable.call();
               } catch (final Exception e) {
                  throw new RuntimeException(e);
               }
            }
         };
      }
   }

   // ==== RequestChannel ======================================================

   /* package */ interface RequestChannel {
      public void shutdown();
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

   @Override
   public final void execute(final Runnable command) {
      if (mChannel.isShutdown()) { throw new RejectedExecutionException(); }

      final RunnableRequest request = new RunnableRequest(command);
      enqueueRequest(request);
   }

   @Override
   public final Future<?> submit(final Runnable task) {
      if (mChannel.isShutdown()) { throw new RejectedExecutionException(); }

      final RunnableRequest request = new RunnableRequest(task);
      enqueueRequest(request);
      return request;
   }

   @Override
   public final <T> Future<T> submit(final Callable<T> task) {
      if (mChannel.isShutdown()) { throw new RejectedExecutionException(); }

      final CallableRequest<T> request = new CallableRequest<T>(task);
      enqueueRequest(request);
      return request;
   }

   @Override
   public final <T> Future<T> submit(final Runnable task, final T result) {
      return submit(new Callable<T>() {
         @Override
         public final T call() {
            task.run();
            return result;
         }
      });
   }

   @Override
   public final <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks)
         throws InterruptedException
   {
      final List<Future<T>> futures = new ArrayList<Future<T>>();

      for (final Callable<T> task : tasks) {
         futures.add(submit(task));
      }

      for (final Future<T> future : futures) {
         try {
            future.get();
         } catch (final ExecutionException e) {
            // ignore
         }
      }

      return futures;
   }

   @Override
   public final <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
                                              final long timeout,
                                              final TimeUnit unit)
         throws InterruptedException
   {
      final List<Future<T>> futures = new ArrayList<Future<T>>();

      for (final Callable<T> task : tasks) {
         futures.add(submit(task));
      }

      final long abortTime = System.currentTimeMillis() + unit.toMillis(timeout);

      for (final Future<T> future : futures) {
         final long timeoutMillis = abortTime - System.currentTimeMillis();

         if (timeoutMillis < 0L) { throw new InterruptedException(); }

         try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
         } catch (final TimeoutException e) {
            throw new InterruptedException();
         } catch (final ExecutionException e) {
            // ignore
         }
      }

      return futures;
   }

   @Override
   public final <T> T invokeAny(final Collection<? extends Callable<T>> tasks)
         throws ExecutionException
   {
      if (tasks.isEmpty()) { throw new IllegalArgumentException(); }

      for (final Callable<T> task : tasks) {
         try {
            return submit(task).get();
         } catch (final Exception e) {
            // continue
         }
      }

      throw new ExecutionException(new Exception());
   }

   @Override
   public final <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
                                final long timeout,
                                final TimeUnit unit)
         throws ExecutionException, TimeoutException
   {
      if (tasks.isEmpty()) { throw new IllegalArgumentException(); }

      final long abortTime = System.currentTimeMillis() + unit.toMillis(timeout);

      for (final Callable<T> task : tasks) {
         final long timeoutMillis = abortTime - System.currentTimeMillis();

         if (timeoutMillis < 0L) { throw new TimeoutException(); }

         try {
            return submit(task).get(timeoutMillis, TimeUnit.MILLISECONDS);
         } catch (final Exception e) {
            // continue
         }
      }

      throw new ExecutionException(new Exception());
   }

   @Override
   public final void shutdown() {
      mChannel.shutdown();
   }

   @Override
   public final List<Runnable> shutdownNow() {
      mChannel.shutdown();

      final List<Runnable> nonCommencedTasks = new ArrayList<Runnable>();

      for (final Request<?> request : mRequests) {
         request.cancel(/* interruptIfRunning = */ true);

         if (!request.isActive()) {
            nonCommencedTasks.add(request.asRunnable());
         }
      }

      return nonCommencedTasks;
   }

   @Override
   public final boolean isShutdown() {
      return mChannel.isShutdown();
   }

   @Override
   public final boolean isTerminated() {
      return mChannel.isShutdown() && mRequests.isEmpty();
   }

   @Override
   public final boolean awaitTermination(final long timeout,
                                         final TimeUnit unit)
         throws InterruptedException
   {
      if (!isShutdown()) {
         shutdown();
      }

      final long abortTime = System.currentTimeMillis() + unit.toMillis(timeout);

      while (!mRequests.isEmpty()) {
         final long timeoutMillis = abortTime - System.currentTimeMillis();

         if (timeoutMillis < 0L) { return false; }

         try {
            mRequests.get(0).get(timeoutMillis, TimeUnit.MILLISECONDS);
         } catch (final ExecutionException e) {
            // ignore
         } catch (final TimeoutException e) {
            throw new InterruptedException();
         }
      }

      return true;
   }
}
