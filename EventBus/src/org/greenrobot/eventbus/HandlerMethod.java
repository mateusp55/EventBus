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

import java.lang.reflect.Method;

/**
 * Used internally by EventBus and generated handler indexes.
 *
 * @author Fabiano Gadelha
 */
public class HandlerMethod {
    final Method method;
    final ExceptionalThreadMode threadMode;
    final ExceptionalActionMode actionMode;
    final Class<?> exceptionalEventType;
    final int priority;
    final boolean sticky;
    /** Used for efficient comparison */
    String methodString;

    public HandlerMethod(Method method, Class<?> exceptionalEventType, ExceptionalThreadMode threadMode, ExceptionalActionMode actionMode, int priority, boolean sticky) {
        this.method = method;
        this.threadMode = threadMode;
        this.exceptionalEventType = exceptionalEventType;
        this.actionMode = actionMode;
        this.priority = priority;
        this.sticky = sticky;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof HandlerMethod) {
            checkMethodString();
            HandlerMethod otherHandlerMethod = (HandlerMethod)other;
            otherHandlerMethod.checkMethodString();
            // Don't use method.equals because of http://code.google.com/p/android/issues/detail?id=7811#c6
            return methodString.equals(otherHandlerMethod.methodString);
        } else {
            return false;
        }
    }

    private synchronized void checkMethodString() {
        if (methodString == null) {
            // Method.toString has more overhead, just take relevant parts of the method
            StringBuilder builder = new StringBuilder(64);
            builder.append(method.getDeclaringClass().getName());
            builder.append('#').append(method.getName());
            builder.append('(').append(exceptionalEventType.getName());
            methodString = builder.toString();
        }
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }
}