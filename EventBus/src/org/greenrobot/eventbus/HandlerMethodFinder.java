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

import org.greenrobot.eventbus.meta.HandlerInfo;
import org.greenrobot.eventbus.meta.HandlerInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Fabiano Gadelha
 */
class HandlerMethodFinder {
    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    private static final Map<Class<?>, List<HandlerMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    private List<HandlerInfoIndex> handlerInfoIndexes;
    private final boolean strictMethodVerification;
    private final boolean ignoreGeneratedIndex;

    private static final int POOL_SIZE = 4;
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    HandlerMethodFinder(List<HandlerInfoIndex> handlerInfoIndexes, boolean strictMethodVerification,
                        boolean ignoreGeneratedIndex) {
        this.handlerInfoIndexes = handlerInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    List<HandlerMethod> findHandlerMethods(Class<?> handlerClass) {
        List<HandlerMethod> handlerMethods = METHOD_CACHE.get(handlerClass);
        if (handlerMethods != null) {
            return handlerMethods;
        }

        if (ignoreGeneratedIndex) {
            handlerMethods = findUsingReflection(handlerClass);
        } else {
            handlerMethods = findUsingInfo(handlerClass);
        }
        if (handlerMethods.isEmpty()) {
            throw new EventBusException("Handler " + handlerClass
                    + " and its super classes have no public methods with the @Handle annotation");
        } else {
            METHOD_CACHE.put(handlerClass, handlerMethods);
            return handlerMethods;
        }
    }

    private List<HandlerMethod> findUsingInfo(Class<?> handlerClass) {
        FindState findState = prepareFindState();
        findState.initForHandler(handlerClass);
        while (findState.clazz != null) {
            findState.handlerInfo = getHandlerInfo(findState);
            if (findState.handlerInfo != null) {
                HandlerMethod[] array = findState.handlerInfo.getHandlerMethods();
                for (HandlerMethod handlerMethod : array) {
                    if (findState.checkAdd(handlerMethod.method, handlerMethod.exceptionalEventType)) {
                        findState.handlerMethods.add(handlerMethod);
                    }
                }
            } else {
                findUsingReflectionInSingleClass(findState);
            }
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    private List<HandlerMethod> getMethodsAndRelease(FindState findState) {
        List<HandlerMethod> handlerMethods = new ArrayList<>(findState.handlerMethods);
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return handlerMethods;
    }

    private FindState prepareFindState() {
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        return new FindState();
    }

    private HandlerInfo getHandlerInfo(FindState findState) {
        if (findState.handlerInfo != null && findState.handlerInfo.getSuperHandlerInfo() != null) {
            HandlerInfo superclassInfo = findState.handlerInfo.getSuperHandlerInfo();
            if (findState.clazz == superclassInfo.getHandlerClass()) {
                return superclassInfo;
            }
        }
        if (handlerInfoIndexes != null) {
            for (HandlerInfoIndex index : handlerInfoIndexes) {
                HandlerInfo info = index.getHandlerInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    private List<HandlerMethod> findUsingReflection(Class<?> handlerClass) {
        FindState findState = prepareFindState();
        findState.initForHandler(handlerClass);
        while (findState.clazz != null) {
            findUsingReflectionInSingleClass(findState);
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // This is faster than getMethods, especially when handlers are fat classes like Activities
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
            try {
                methods = findState.clazz.getMethods();
            } catch (LinkageError error) { // super class of NoClassDefFoundError to be a bit more broad...
                String msg = "Could not inspect methods of " + findState.clazz.getName();
                if (ignoreGeneratedIndex) {
                    msg += ". Please consider using EventBus annotation processor to avoid reflection.";
                } else {
                    msg += ". Please make this class visible to EventBus annotation processor to avoid reflection.";
                }
                throw new EventBusException(msg, error);
            }
            findState.skipSuperClasses = true;
        }
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    Handle handleAnnotation = method.getAnnotation(Handle.class);
                    if (handleAnnotation != null) {
                        Class<?> exceptionalEventType = parameterTypes[0];
                        if (findState.checkAdd(method, exceptionalEventType)) {
                            ExceptionalThreadMode threadMode = handleAnnotation.threadMode();
                            findState.handlerMethods.add(new HandlerMethod(method, exceptionalEventType, threadMode,
                                    handleAnnotation.priority(), handleAnnotation.sticky()));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Handle.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Handle method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Handle.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Handle method: must be public, non-static, and non-abstract");
            }
        }
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
    }

    static class FindState {
        final List<HandlerMethod> handlerMethods = new ArrayList<>();
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        final Map<String, Class> handlerClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        Class<?> handlerClass;
        Class<?> clazz;
        boolean skipSuperClasses;
        HandlerInfo handlerInfo;

        void initForHandler(Class<?> handlerClass) {
            this.handlerClass = clazz = handlerClass;
            skipSuperClasses = false;
            handlerInfo = null;
        }

        void recycle() {
            handlerMethods.clear();
            anyMethodByEventType.clear();
            handlerClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            handlerClass = null;
            clazz = null;
            skipSuperClasses = false;
            handlerInfo = null;
        }

        boolean checkAdd(Method method, Class<?> exceptionalEventType) {
            // 2 level check: 1st level with exceptional event type only (fast), 2nd level with complete signature when required.
            // Usually a handler doesn't have methods listening to the same exceptional event type.
            Object existing = anyMethodByEventType.put(exceptionalEventType, method);
            if (existing == null) {
                return true;
            } else {
                if (existing instanceof Method) {
                    if (!checkAddWithMethodSignature((Method) existing, exceptionalEventType)) {
                        // Paranoia check
                        throw new IllegalStateException();
                    }
                    // Put any non-Method object to "consume" the existing Method
                    anyMethodByEventType.put(exceptionalEventType, this);
                }
                return checkAddWithMethodSignature(method, exceptionalEventType);
            }
        }

        private boolean checkAddWithMethodSignature(Method method, Class<?> exceptionalEventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(exceptionalEventType.getName());

            String methodKey = methodKeyBuilder.toString();
            Class<?> methodClass = method.getDeclaringClass();
            Class<?> methodClassOld = handlerClassByMethodKey.put(methodKey, methodClass);
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                // Only add if not already found in a sub class
                return true;
            } else {
                // Revert the put, old class is further down the class hierarchy
                handlerClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        void moveToSuperclass() {
            if (skipSuperClasses) {
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                // Skip system classes, this degrades performance.
                // Also we might avoid some ClassNotFoundException (see FAQ for background).
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") ||
                        clazzName.startsWith("android.") || clazzName.startsWith("androidx.")) {
                    clazz = null;
                }
            }
        }
    }

}
