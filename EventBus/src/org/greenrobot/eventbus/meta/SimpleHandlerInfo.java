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

import org.greenrobot.eventbus.HandlerMethod;

/**
 * Uses {@link HandlerMethodInfo} objects to create {@link HandlerMethod} objects on demand.
 */
public class SimpleHandlerInfo extends AbstractHandlerInfo {

    private final HandlerMethodInfo[] methodInfos;

    public SimpleHandlerInfo(Class handlerClass, boolean shouldCheckSuperclass, HandlerMethodInfo[] methodInfos) {
        super(handlerClass, null, shouldCheckSuperclass);
        this.methodInfos = methodInfos;
    }

    @Override
    public synchronized HandlerMethod[] getHandlerMethods() {
        int length = methodInfos.length;
        HandlerMethod[] methods = new HandlerMethod[length];
        for (int i = 0; i < length; i++) {
            HandlerMethodInfo info = methodInfos[i];
            methods[i] = createHandlerMethod(info.methodName, info.eventType, info.threadMode, info.actionMode,
                    info.priority, info.sticky);
        }
        return methods;
    }
}