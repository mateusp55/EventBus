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

/**
 * @author Fabiano Gadelha
 */
final class Handlement {
    final Object handler;
    final HandlerMethod handlerMethod;
    /**
     * Becomes false as soon as {@link EventBus#unregisterHandler(Object)} is called, which is checked by queued exceptional event delivery
     * {@link EventBus#invokeHandler(PendingThrow)} to prevent race conditions.
     */
    volatile boolean active;

    Handlement(Object handler, HandlerMethod handlerMethod) {
        this.handler = handler;
        this.handlerMethod = handlerMethod;
        active = true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Handlement) {
            Handlement otherHandlement = (Handlement) other;
            return handler == otherHandlement.handler
                    && handlerMethod.equals(otherHandlement.handlerMethod);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return handler.hashCode() + handlerMethod.methodString.hashCode();
    }
}