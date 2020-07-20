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
 * This Exceptional event is throwed by EventBus when no handler is found for a throwed exceptinal event.
 * 
 * @author Fabiano Gadelha
 */
public final class NoHandlerExceptionalEvent {
    /** The {@link EventBus} instance to with the original exceptional event was throwed to. */
    public final EventBus eventBus;

    /** The original exceptional event that could not be delivered to any handler. */
    public final Object originalExceptionalEvent;

    public NoHandlerExceptionalEvent(EventBus eventBus, Object originalExceptionalEvent) {
        this.eventBus = eventBus;
        this.originalExceptionalEvent = originalExceptionalEvent;
    }

}
