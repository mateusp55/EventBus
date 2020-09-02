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
 * Each subscriber method has a action mode, which determines what
 * type of action will be taken to execute the method.
 *
 * @author Fabiano Gadelha
 */
public enum ActionMode {
    /**
     * This is default action, in which the method will receive the invocation and will be executed.
     * However, the method will only be executed if the class instance is registered.
     */
    SUBSCRIBE,
    /**
     * This action causes the method to receive the invocation to be executed, even if there is no instance
     * of the registered class. This action ensures that the class is initialized so that the method is executed.
     */
    START_AND_SUBSCRIBE
}