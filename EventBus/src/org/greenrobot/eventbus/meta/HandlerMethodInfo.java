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
package org.greenrobot.eventbus.meta;

import org.greenrobot.eventbus.ExceptionalThreadMode;

public class HandlerMethodInfo {
    final String methodName;
    final ExceptionalThreadMode threadMode;
    final Class<?> eventType;
    final int priority;
    final boolean sticky;

    public HandlerMethodInfo(String methodName, Class<?> eventType, ExceptionalThreadMode threadMode,
                             int priority, boolean sticky) {
        this.methodName = methodName;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.priority = priority;
        this.sticky = sticky;
    }

    public HandlerMethodInfo(String methodName, Class<?> eventType) {
        this(methodName, eventType, ExceptionalThreadMode.THROWING, 0, false);
    }

    public HandlerMethodInfo(String methodName, Class<?> eventType, ExceptionalThreadMode threadMode) {
        this(methodName, eventType, threadMode, 0, false);
    }

}