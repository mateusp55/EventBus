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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Fabiano Gadelha
 */
final class PendingThrow {
    private final static List<PendingThrow> pendingThrowPool = new ArrayList<PendingThrow>();

    Object exceptionalEvent;
    Handlement handlement;
    PendingThrow next;

    private PendingThrow(Object exceptionalEvent, Handlement handlement) {
        this.exceptionalEvent = exceptionalEvent;
        this.handlement = handlement;
    }

    static PendingThrow obtainPendingThrow(Handlement handlement, Object exceptionalEvent) {
        synchronized (pendingThrowPool) {
            int size = pendingThrowPool.size();
            if (size > 0) {
                PendingThrow pendingThrow = pendingThrowPool.remove(size - 1);
                pendingThrow.exceptionalEvent = exceptionalEvent;
                pendingThrow.handlement = handlement;
                pendingThrow.next = null;
                return pendingThrow;
            }
        }
        return new PendingThrow(exceptionalEvent, handlement);
    }

    static void releasePendingThrow(PendingThrow pendingThrow) {
        pendingThrow.exceptionalEvent = null;
        pendingThrow.handlement = null;
        pendingThrow.next = null;
        synchronized (pendingThrowPool) {
            // Don't let the pool grow indefinitely
            if (pendingThrowPool.size() < 10000) {
                pendingThrowPool.add(pendingThrow);
            }
        }
    }

}