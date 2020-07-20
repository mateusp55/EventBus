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


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Fabiano Gadelha
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Handle {
    ExceptionalThreadMode threadMode() default ExceptionalThreadMode.THROWING;

    /**
     * If true, delivers the most recent sticky exceptional event (throwed with
     * {@link EventBus#throwsSticky(Object)}) to this handler (if exceptional event available).
     */
    boolean sticky() default false;

    /** Handler priority to influence the order of exceptional event delivery.
     * Within the same delivery thread ({@link ExceptionalThreadMode}), higher priority handlers will receive exceptional events before
     * others with a lower priority. The default priority is 0. Note: the priority does *NOT* affect the order of
     * delivery among handlers with different {@link ExceptionalThreadMode}s! */
    int priority() default 0;
}

