/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import java.util.logging.Level;

/**
 * Throws exceptional events in background.
 *
 * @author Fabiano Gadelha
 */
final class BackgroundThrower implements Runnable, Thrower {

    private final PendingThrowQueue queue;
    private final EventBus eventBus;

    private volatile boolean executorRunning;

    BackgroundThrower(EventBus eventBus) {
        this.eventBus = eventBus;
        queue = new PendingThrowQueue();
    }

    public void enqueue(Handlement handlement, Object event) {
        PendingThrow pendingThrow = PendingThrow.obtainPendingThrow(handlement, event);
        synchronized (this) {
            queue.enqueue(pendingThrow);
            if (!executorRunning) {
                executorRunning = true;
                eventBus.getExecutorService().execute(this);
            }
        }
    }

    @Override
    public void run() {
        try {
            try {
                while (true) {
                    PendingThrow pendingThrow = queue.poll(1000);
                    if (pendingThrow == null) {
                        synchronized (this) {
                            // Check again, this time in synchronized
                            pendingThrow = queue.poll();
                            if (pendingThrow == null) {
                                executorRunning = false;
                                return;
                            }
                        }
                    }
                    eventBus.invokeHandler(pendingThrow);
                }
            } catch (InterruptedException e) {
                eventBus.getLogger().log(Level.WARNING, Thread.currentThread().getName() + " was interruppted", e);
            }
        } finally {
            executorRunning = false;
        }
    }

}
