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

import org.greenrobot.eventbus.EventBusException;
import org.greenrobot.eventbus.ExceptionalActionMode;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.HandlerMethod;

import java.lang.reflect.Method;

/** Base class for generated handler meta info classes created by annotation processing. */
public abstract class AbstractHandlerInfo implements HandlerInfo {
    private final Class handlerClass;
    private final Class<? extends HandlerInfo> superHandlerInfoClass;
    private final boolean shouldCheckSuperclass;

    protected AbstractHandlerInfo(Class handlerClass, Class<? extends HandlerInfo> superHandlerInfoClass,
                                  boolean shouldCheckSuperclass) {
        this.handlerClass = handlerClass;
        this.superHandlerInfoClass = superHandlerInfoClass;
        this.shouldCheckSuperclass = shouldCheckSuperclass;
    }

    @Override
    public Class getHandlerClass() {
        return handlerClass;
    }

    @Override
    public HandlerInfo getSuperHandlerInfo() {
        if(superHandlerInfoClass == null) {
            return null;
        }
        try {
            return superHandlerInfoClass.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean shouldCheckSuperclass() {
        return shouldCheckSuperclass;
    }

    protected HandlerMethod createHandlerMethod(String methodName, Class<?> eventType) {
        return createHandlerMethod(methodName, eventType, ExceptionalThreadMode.THROWING, ExceptionalActionMode.HANDLE, 0, false);
    }

    protected HandlerMethod createHandlerMethod(String methodName, Class<?> eventType, ExceptionalThreadMode threadMode, ExceptionalActionMode actionMode) {
        return createHandlerMethod(methodName, eventType, threadMode, actionMode, 0, false);
    }

    protected HandlerMethod createHandlerMethod(String methodName, Class<?> eventType, ExceptionalThreadMode threadMode, ExceptionalActionMode actionMode,
                                                int priority, boolean sticky) {
        try {
            Method method = handlerClass.getDeclaredMethod(methodName, eventType);
            return new HandlerMethod(method, eventType, threadMode, actionMode, priority, sticky);
        } catch (NoSuchMethodException e) {
            throw new EventBusException("Could not find handler method in " + handlerClass +
                    ". Maybe a missing ProGuard rule?", e);
        }
    }

}