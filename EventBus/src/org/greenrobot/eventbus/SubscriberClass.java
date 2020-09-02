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
final class SubscriberClass {
    final Class<?> subscriberClass;
    final SubscriberMethod subscriberMethod;
    volatile boolean active;

    SubscriberClass(Class<?> subscriberClass, SubscriberMethod subscriberMethod) {
        this.subscriberClass = subscriberClass;
        this.subscriberMethod = subscriberMethod;
        active = true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SubscriberClass) {
            SubscriberClass otherSubscription = (SubscriberClass) other;
            return subscriberClass == otherSubscription.subscriberClass
                    && subscriberMethod.equals(otherSubscription.subscriberMethod);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return subscriberClass.hashCode() + subscriberMethod.methodString.hashCode();
    }
}