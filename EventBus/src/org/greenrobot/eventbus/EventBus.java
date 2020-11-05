/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import dalvik.system.DexFile;

/**
 * EventBus is a central publish/subscribe event system for Java and Android.
 * Events are posted ({@link #post(Object)}) to the bus, which delivers it to subscribers that have a matching handler
 * method for the event type.
 * To receive events, subscribers must register themselves to the bus using {@link #registerSubscriber(Object)}.
 * Once registered, subscribers receive events until {@link #unregisterSubscriber(Object)} is called.
 * Event handling methods must be annotated by {@link Subscribe}, must be public, return nothing (void),
 * and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /** Log tag, apps may override it. */
    public static String TAG = "EventBus";

    static volatile EventBus defaultInstance;

    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();
    private static final Map<Class<?>, List<Class<?>>> exceptionalEventTypesCache = new HashMap<>();

    private final Map<Class<?>, CopyOnWriteArrayList<SubscriberClass>> subscriberClassesByEventType;
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;

    private final Map<Class<?>, CopyOnWriteArrayList<HandlerClass>> handlerClassesByExceptionalEventType;
    private final Map<Class<?>, CopyOnWriteArrayList<Handlement>> handlementsByExceptionalEventType;
    private final Map<Object, List<Class<?>>> typesByHandler;
    private final Map<Class<?>, Object> stickyExceptionalEvents;

    private android.content.Context context;

    /**
     * Current Immediate Thread State.
     *
     * For events and exceptional events sent immediately to objects already instantiated.
     * */

    private final ThreadLocal<PostingThreadState> currentImmediatePostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState(false);
        }
    };

    private final ThreadLocal<ThrowingThreadState> currentImmediateThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(false);
        }
    };

    /**
     * Current Late Thread State.
     *
     * For events and exceptional events sent late for objects that are yet to be instantiated.
     * */

    private final ThreadLocal<PostingThreadState> currentLatePostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState(true);
        }
    };

    private final ThreadLocal<ThrowingThreadState> currentLateThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(true);
        }
    };

    // @Nullable
    private final MainThreadSupport mainThreadSupport;
    // @Nullable
    private final Poster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final Thrower mainThreadThrower;
    private final BackgroundThrower backgroundThrower;
    private final AsyncThrower asyncThrower;
    private final HandlerMethodFinder handlerMethodFinder;
    private final ExecutorService executorService;

    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;

    private final boolean throwHandlerException;
    private final boolean logHandlerExceptions;
    private final boolean logNoHandlerMessages;
    private final boolean sendHandlerExceptionExceptionalEvent;
    private final boolean sendNoHandlerExceptionalEvent;
    private final boolean exceptionalEventInheritance;

    private boolean mappedClassesRegistrationPerformed;

    private final int indexCount;
    private final int indexCountSubscriber;
    private final int indexCountHandler;
    private final Logger logger;

    /** Convenience singleton for apps using a process-wide EventBus instance. */
    public static EventBus getDefault() {
        return getDefault((Context) null);
    }

    /** Convenience singleton for apps using a process-wide EventBus instance. */
    public static EventBus getDefault(android.content.Context context) {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus((Context) context);
                }
            }
        }
        return instance;
    }

    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /** For unit test primarily. */
    public static void clearCaches() {
        /** Subcribers */
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
        /** Handlers */
        HandlerMethodFinder.clearCaches();
        exceptionalEventTypesCache.clear();
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
     * central bus, consider {@link #getDefault()}.
     */
    public EventBus() {
        this((Context) null);
    }


    public EventBus(android.content.Context context) {
        this(DEFAULT_BUILDER);
        this.context = context;
    }

    EventBus(EventBusBuilder builder) {
        logger = builder.getLogger();

        /** Post/Subcribers */
        subscriberClassesByEventType = new HashMap<>();
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
        /** Throwers/Handlers */
        handlerClassesByExceptionalEventType = new HashMap<>();
        handlementsByExceptionalEventType = new HashMap<>();
        typesByHandler = new HashMap<>();
        stickyExceptionalEvents = new ConcurrentHashMap<>();

        mainThreadSupport = builder.getMainThreadSupport();

        /** Post/Subcribers */
        mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        /** Throwers/Handlers */
        mainThreadThrower = mainThreadSupport != null ? mainThreadSupport.createThrower(this) : null;
        backgroundThrower = new BackgroundThrower(this);
        asyncThrower = new AsyncThrower(this);

        indexCountSubscriber = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
        indexCountHandler = builder.handlerInfoIndexes != null ? builder.handlerInfoIndexes.size() : 0;
        indexCount = indexCountSubscriber + indexCountHandler;

        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);
        handlerMethodFinder = new HandlerMethodFinder(builder.handlerInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);

        executorService = builder.executorService;
        /** Post/Subcribers */
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;

        /** Throwers/Handlers */
        logHandlerExceptions = builder.logHandlerExceptions;
        logNoHandlerMessages = builder.logNoHandlerMessages;
        sendHandlerExceptionExceptionalEvent = builder.sendHandlerExceptionExceptionalEvent;
        sendNoHandlerExceptionalEvent = builder.sendNoHandlerExceptionalEvent;
        throwHandlerException = builder.throwHandlerException;
        exceptionalEventInheritance = builder.exceptionalEventInheritance;

        mappedClassesRegistrationPerformed = builder.mappedClassesRegistrationPerformed;
    }

    /**
     * Registers the given object to receive both events and exceptional events. Registered objects must call {@link #unregister(Object)} once they
     * are no longer interested in receiving events and exceptional events.
     */
    public void register(Object object) {
        registerSubscriber(object);
        registerHandler(object);
    }

    /**
     * Registers the given subscriber to receive events. Subscribers must call {@link #unregisterSubscriber(Object)} once they
     * are no longer interested in receiving events.
     * <p/>
     * Subscribers have event handling methods that must be annotated by {@link Subscribe}.
     * The {@link Subscribe} annotation also allows configuration like {@link
     * ThreadMode} and priority.
     */
    public void registerSubscriber(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }

        //Processes the thread that sends the messages that are in the late queue.
        PostingThreadState latePostingState = currentLatePostingThreadState.get();
        processPostingThread(subscriber, latePostingState);
    }

    /**
     * Registers the given handler to receive exceptional events. Handlers must call {@link #unregisterHandler(Object)} once they
     * are no longer interested in receiving exceptional events.
     * <p/>
     * Handlers have exceptional event handling methods that must be annotated by {@link Handle}.
     * The {@link Handle} annotation also allows configuration like {@link
     * ExceptionalThreadMode} and priority.
     */
    public void registerHandler(Object handler) {
        Class<?> handlerClass = handler.getClass();
        List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(handlerClass);
        synchronized (this) {
            for (HandlerMethod handlerMethod : handlerMethods) {
                handle(handler, handlerMethod);
            }

            //Processes the thread that sends the messages that are in the late queue.
            ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
            processThrowingThread(handler, lateThrowingState);
        }
    }

    // Must be called in synchronized block
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        if (subscriberMethod.sticky) {
            if (eventInheritance) {
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    // Must be called in synchronized block
    private void handle(Object handler, HandlerMethod handlerMethod) {
        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        Handlement newHandlement = new Handlement(handler, handlerMethod);
        CopyOnWriteArrayList<Handlement> handlements = handlementsByExceptionalEventType.get(exceptionalEventType);
        if (handlements == null) {
            handlements = new CopyOnWriteArrayList<>();
            handlementsByExceptionalEventType.put(exceptionalEventType, handlements);
        } else {
            if (handlements.contains(newHandlement)) {
                throw new EventBusException("Handler " + handler.getClass() + " already registered to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = handlements.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > handlements.get(i).handlerMethod.priority) {
                handlements.add(i, newHandlement);
                break;
            }
        }

        List<Class<?>> handledExceptionalEvents = typesByHandler.get(handler);
        if (handledExceptionalEvents == null) {
            handledExceptionalEvents = new ArrayList<>();
            typesByHandler.put(handler, handledExceptionalEvents);
        }
        handledExceptionalEvents.add(exceptionalEventType);

        if (handlerMethod.sticky) {
            if (exceptionalEventInheritance) {
                // Existing sticky exceptional events of all subclasses of exceptionalEventType have to be considered.
                // Note: Iterating over all exceptional events may be inefficient with lots of sticky exceptional events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyExceptionalEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateExceptionalEventType = entry.getKey();
                    if (exceptionalEventType.isAssignableFrom(candidateExceptionalEventType)) {
                        Object stickyExceptionalEvent = entry.getValue();
                        checkThrowsStickyExceptionalEventToHandlement(newHandlement, stickyExceptionalEvent);
                    }
                }
            } else {
                Object stickyExceptionalEvent = stickyExceptionalEvents.get(exceptionalEventType);
                checkThrowsStickyExceptionalEventToHandlement(newHandlement, stickyExceptionalEvent);
            }
        }
    }

    // Must be called in synchronized block
    private void subscribeClass(Class<?> subscriberClassType, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        SubscriberClass newSubscriberClass = new SubscriberClass(subscriberClassType, subscriberMethod);
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses = subscriberClassesByEventType.get(eventType);
        if (subscriberClasses == null) {
            subscriberClasses = new CopyOnWriteArrayList<>();
            subscriberClassesByEventType.put(eventType, subscriberClasses);
        } else {
            if (subscriberClasses.contains(newSubscriberClass)) {
                throw new EventBusException("Subscriber " + subscriberClassType + " already registered as 'Subscriber Class' to event "
                        + eventType);
            }
        }

        int size = subscriberClasses.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriberClasses.get(i).subscriberMethod.priority) {
                subscriberClasses.add(i, newSubscriberClass);
                break;
            }
        }
    }

    // Must be called in synchronized block
    private void handleClass(Class<?> handlerClassType, HandlerMethod handlerMethod) {
        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        HandlerClass newHandlerClass = new HandlerClass(handlerClassType, handlerMethod);
        CopyOnWriteArrayList<HandlerClass> handlerClasses = handlerClassesByExceptionalEventType.get(exceptionalEventType);
        if (handlerClasses == null) {
            handlerClasses = new CopyOnWriteArrayList<>();
            handlerClassesByExceptionalEventType.put(exceptionalEventType, handlerClasses);
        } else {
            if (handlerClasses.contains(newHandlerClass)) {
                throw new EventBusException("Handler " + handlerClassType + " already registered as 'Hendler Class' to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = handlerClasses.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > handlerClasses.get(i).handlerMethod.priority) {
                handlerClasses.add(i, newHandlerClass);
                break;
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    private void checkThrowsStickyExceptionalEventToHandlement(Handlement newHandlement, Object stickyExceptionalEvent) {
        if (stickyExceptionalEvent != null) {
            // If the handler is trying to abort the exceptional event, it will fail (exceptional event is not tracked in throwing state)
            // --> Strange corner case, which we don't take care of here.
            throwsToHandlement(newHandlement, stickyExceptionalEvent, isMainThread());
        }
    }

    /**
     * Checks if the current thread is running in the main thread.
     * If there is no main thread support (e.g. non-Android), "true" is always returned. In that case MAIN thread
     * subscribers are always called in posting thread, and BACKGROUND subscribers are always called from a background
     * poster.
     */
    private boolean isMainThread() {
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    public synchronized boolean isRegisteredHandler(Object handler) {
        return typesByHandler.containsKey(handler);
    }

    /** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber. */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /** Only updates handlementsByExceptionalEventType, not typesByHandler! Caller must update typesByHandler. */
    private void unhandleByExceptionalEventType(Object handler, Class<?> exceptionalEventType) {
        List<Handlement> handlements = handlementsByExceptionalEventType.get(exceptionalEventType);
        if (handlements != null) {
            int size = handlements.size();
            for (int i = 0; i < size; i++) {
                Handlement handlement = handlements.get(i);
                if (handlement.handler == handler) {
                    handlement.active = false;
                    handlements.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /** Unregisters the given object from all event and exceptional event classes. */
    public synchronized void unregister(Object object) {
        unregisterSubscriber(object);
        unregisterHandler(object);
    }

    /** Unregisters the given subscriber from all event classes. */
    public synchronized void unregisterSubscriber(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /** Unregisters the given handler from all exceptional event classes. */
    public synchronized void unregisterHandler(Object handler) {
        List<Class<?>> handledTypes = typesByHandler.get(handler);
        if (handledTypes != null) {
            for (Class<?> exceptionalEventType : handledTypes) {
                unhandleByExceptionalEventType(handler, exceptionalEventType);
            }
            typesByHandler.remove(handler);
        } else {
            logger.log(Level.WARNING, "Handler to unregister was not registered before: " + handler.getClass());
        }
    }

    /** Posts the given event to the event bus. */
    public void post(Object event) {
        synchronized (event) {
            //Register classes with methods mapped as subscribe or handle.
            try {
                registerMappedClasses();
            } catch(NoClassDefFoundError e) {
                //At the moment, do nothing.
            }

            //Put exceptional events in immediate queue.
            PostingThreadState immediatePostingState = currentImmediatePostingThreadState.get();
            List<Object> immediateEventQueue = immediatePostingState.eventQueue;
            immediateEventQueue.add(event);

            //Processes the thread that sends the messages that are in the immediate queue.
            processPostingThread(immediatePostingState);

            //Put events in late queue.
            PostingThreadState latePostingState = currentLatePostingThreadState.get();
            List<Object> lateEventQueue = latePostingState.eventQueue;
            lateEventQueue.add(event);

            //Prepare to start the activities that will receive the events of the late queue.
            prepareLatePostingEvent(event);
        }
    }

    /** Posts the given exceptional event to the event bus. */
    public void throwsException(Object exceptionalEvent) {
        synchronized (exceptionalEvent) {
            //Register classes with methods mapped as subscribe or handle.
            try {
                registerMappedClasses();
            } catch(NoClassDefFoundError e) {
                //At the moment, do nothing.
            }

            //Put exceptional events in immediate queue.
            ThrowingThreadState immediateThrowingState = currentImmediateThrowingThreadState.get();
            List<Object> immediateEventQueue = immediateThrowingState.exceptionalEventQueue;
            immediateEventQueue.add(exceptionalEvent);

            //Processes the thread that sends the messages that are in the immediate queue.
            processThrowingThread(immediateThrowingState);

            //Put exceptional events in late queue.
            ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
            List<Object> lateEventQueue = lateThrowingState.exceptionalEventQueue;
            lateEventQueue.add(exceptionalEvent);

            //Prepare to start the activities that will receive the exceptional events of the late queue.
            prepareLateThrowingExceptionalEvent(exceptionalEvent);
        }
    }

    public static boolean isIntentAvailable(Context ctx, Intent intent) {
        final PackageManager mgr = ctx.getPackageManager();
        List<ResolveInfo> list =
                mgr.queryIntentActivities(intent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public void registerMappedClasses() {
        if(mappedClassesRegistrationPerformed)
            return;

        mappedClassesRegistrationPerformed = true;

        if(context == null)
            return;

        try {
            @SuppressLint({"NewApi", "LocalSuppress"})
            DexFile df = new DexFile(context.getPackageCodePath());

            for (Enumeration<String> iter = df.entries(); iter.hasMoreElements(); ) {
                String s = iter.nextElement();
                if (s.contains(context.getPackageName())) {
                    //System.out.println("ClasseDex: " + s);

                    try {
                        Class<?> classInPackage = Class.forName(s);

                        boolean hasSubscriberMethods = subscriberMethodFinder.hasSubscriberMethods(classInPackage);
                        boolean hasHandlerMethods = handlerMethodFinder.hasHandlerMethods(classInPackage);

                        //Register classes that contains methods mapped with the @Subscribe annotation.
                        if(hasSubscriberMethods) {
                            List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(classInPackage);
                            synchronized (this) {
                                for (SubscriberMethod subscriberMethod : subscriberMethods) {
                                    subscribeClass(classInPackage, subscriberMethod);
                                }
                            }
                        }

                        //Register classes that contains methods mapped with the @Handle annotation.
                        if(hasHandlerMethods) {
                            List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(classInPackage);
                            synchronized (this) {
                                for (HandlerMethod handlerMethod : handlerMethods) {
                                    handleClass(classInPackage, handlerMethod);
                                }
                            }
                        }

                        if(hasSubscriberMethods || hasHandlerMethods) {
                            System.out.println("REGISTERED: MappedClass [ " + classInPackage.getName() + "]");
                        }
                    } catch (ClassNotFoundException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link Subscribe#priority()}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#POSTING}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentImmediatePostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.POSTING) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Called from a handler's exceptional event handling method, further exceptional event delivery will be canceled. Subsequent
     * handlers won't receive the exceptional event. Exceptional events are usually canceled by higher priority handlers (see
     * {@link Handle#priority()}). Canceling is restricted to exceptional event handling methods running in throwing thread
     * {@link ExceptionalThreadMode#THROWING}.
     */
    public void cancelExceptionalEventDelivery(Object exceptionalEvent) {
        ThrowingThreadState throwingState = currentImmediateThrowingThreadState.get();
        if (!throwingState.isThrowing) {
            throw new EventBusException(
                    "This method may only be called from inside exceptional event handling methods on the throwing thread");
        } else if (exceptionalEvent == null) {
            throw new EventBusException("Exceptional event may not be null");
        } else if (throwingState.exceptionalEvent != exceptionalEvent) {
            throw new EventBusException("Only the currently handled exceptional event may be aborted");
        } else if (throwingState.handlement.handlerMethod.threadMode != ExceptionalThreadMode.THROWING) {
            throw new EventBusException(" exceptional event handlers may only abort the incoming exceptional event");
        }

        throwingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Posts the given exceptional event to the event bus and holds on to the exceptional event (because it is sticky). The most recent sticky
     * exceptional event of an exceptional event's type is kept in memory for future access by handlers using {@link Handle#sticky()}.
     */
    public void throwsSticky(Object exceptionalEvent) {
        synchronized (stickyExceptionalEvents) {
            stickyExceptionalEvents.put(exceptionalEvent.getClass(), exceptionalEvent);
        }
        // Should be throwed after it is putted, in case the handler wants to remove immediately
        throwsException(exceptionalEvent);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Gets the most recent sticky exceptional event for the given type.
     *
     * @see #throwsSticky(Object)
     */
    public <T> T getStickyExceptionalEvent(Class<T> exceptionalEventType) {
        synchronized (stickyExceptionalEvents) {
            return exceptionalEventType.cast(stickyExceptionalEvents.get(exceptionalEventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky exceptional event for the given exceptional event type.
     *
     * @see #throwsSticky(Object)
     */
    public <T> T removeStickyExceptionalEvent(Class<T> exceptionalEventType) {
        synchronized (stickyExceptionalEvents) {
            return exceptionalEventType.cast(stickyExceptionalEvents.remove(exceptionalEventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes the sticky exceptional event if it equals to the given exceptional event.
     *
     * @return true if the events matched and the sticky exceptional event was removed.
     */
    public boolean removeStickyExceptionalEvent(Object exceptionalEvent) {
        synchronized (stickyExceptionalEvents) {
            Class<?> exceptionalEventType = exceptionalEvent.getClass();
            Object existingeExceptionalEvent = stickyExceptionalEvents.get(exceptionalEvent);
            if (exceptionalEvent.equals(existingeExceptionalEvent)) {
                stickyExceptionalEvents.remove(exceptionalEventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    /**
     * Removes all sticky exceptional events.
     */
    public void removeAllExceptionalStickyEvents() {
        synchronized (stickyExceptionalEvents) {
            stickyExceptionalEvents.clear();
        }
    }

    private void processPostingThread(PostingThreadState postingState) {
        processPostingThread(null, postingState);
    }

    private void processPostingThread(Object subscriber, PostingThreadState postingState) {
        List<Object> eventQueue = postingState.eventQueue;

        if (!postingState.isPosting) {
            postingState.isMainThread = isMainThread();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                if (subscriber != null) {
                    Iterator<Object> it = eventQueue.iterator();
                    while(it.hasNext()) {
                        Object event = it.next();
                        if(isSubscriberForEvent(this, event)) {
                            postSingleEvent(event, postingState);
                            it.remove();
                        }
                    }
                }
                else {
                    while (!eventQueue.isEmpty()) {
                        postSingleEvent(eventQueue.remove(0), postingState);
                    }
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    private void processThrowingThread(ThrowingThreadState throwingState) {
        processThrowingThread(null, throwingState);
    }

    private void processThrowingThread(Object handler, ThrowingThreadState throwingState) {
        List<Object> eventQueue = throwingState.exceptionalEventQueue;

        if (!throwingState.isThrowing) {
            throwingState.isMainThread = isMainThread();
            throwingState.isThrowing = true;
            if (throwingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                if (handler != null) {
                    Iterator<Object> it = eventQueue.iterator();
                    while(it.hasNext()) {
                        Object exceptionalEvent = it.next();
                        if(isHandlerForExceptionalEvent(handler, exceptionalEvent)) {
                            throwsSingleExceptionalEvent(exceptionalEvent, throwingState);
                            it.remove();
                        }
                    }
                }
                else {
                    while (!eventQueue.isEmpty()) {
                        throwsSingleExceptionalEvent(eventQueue.remove(0), throwingState);
                    }
                }
            } finally {
                throwingState.isThrowing = false;
                throwingState.isMainThread = false;
            }
        }
    }

    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    private void throwsSingleExceptionalEvent(Object exceptionalEvent, ThrowingThreadState throwingState) throws Error {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean handlementFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlementFound |= throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, throwingState, clazz);
            }
        } else {
            handlementFound = throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, throwingState, exceptionalEventClass);
        }
        if (!handlementFound) {
            if (logNoHandlerMessages) {
                logger.log(Level.FINE, "No handlers registered for exceptional event " + exceptionalEventClass);
            }
            if (sendNoHandlerExceptionalEvent && exceptionalEventClass != NoHandlerExceptionalEvent.class &&
                    exceptionalEventClass != HandlerExceptionExceptionalEvent.class) {
                throwsException(new NoHandlerExceptionalEvent(this, exceptionalEvent));
            }
        }
    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private boolean throwsSingleExceptionalEventForExceptionalEventType(Object exceptionalEvent, ThrowingThreadState throwingState, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
        }
        if (handlements != null && !handlements.isEmpty()) {
            for (Handlement handlement : handlements) {
                throwingState.exceptionalEvent = exceptionalEvent;
                throwingState.handlement = handlement;
                boolean aborted;
                try {
                    throwsToHandlement(handlement, exceptionalEvent, throwingState.isMainThread);
                    aborted = throwingState.canceled;
                } finally {
                    throwingState.exceptionalEvent = null;
                    throwingState.handlement = null;
                    throwingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public boolean hasSubscriberForEvent(Object event) {
        return (event == null) ? false : hasSubscriberForEventClass(event.getClass());
    }

    public boolean hasHandlerForExceptionalEvent(Object exceptionalEvent) {
        return (exceptionalEvent == null) ? false : hasHandlerForExceptionalEventClass(exceptionalEvent.getClass());
    }

    public boolean hasSubscriberForEventClass(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if (hasSubscriptionForEventType(clazz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasHandlerForExceptionalEventClass(Class<?> exceptionalEventClass) {
        List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
        if (exceptionalEventTypes != null) {
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if (hasHandlementForExceptionalEventType(clazz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions = null;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
            return subscriptions != null && !subscriptions.isEmpty();
        }
    }

    private boolean hasHandlementForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements = null;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
            return handlements != null && !handlements.isEmpty();
        }
    }

    public boolean hasSubscriberClassForEvent(Object event) {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= hasSubscriptionClassForEventType(clazz);
            }
        } else {
            subscriptionFound = hasSubscriptionClassForEventType(eventClass);
        }
        return subscriptionFound;
    }

    public boolean hasHandlerClassForExceptionalEvent(Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean handlementFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlementFound |= hasHandlementClassForExceptionalEventType(clazz);
            }
        } else {
            handlementFound = hasHandlementClassForExceptionalEventType(exceptionalEventClass);
        }
        return handlementFound;
    }

    private boolean hasSubscriptionClassForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = subscriberClassesByEventType.get(eventClass);
            return subscriberClasses != null && !subscriberClasses.isEmpty();
        }
    }

    private boolean hasHandlementClassForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = handlerClassesByExceptionalEventType.get(exceptionalEventClass);
            return handlerClasses != null && !handlerClasses.isEmpty();
        }
    }

    private boolean isSubscriberForEvent(Object subscriber, Object event) {
        Class<?> eventClass = event.getClass();
        Class<?> subscriberClass = subscriber.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= isSubscriberForEventType(subscriberClass, clazz);
            }
        } else {
            subscriptionFound = isSubscriberForEventType(subscriberClass, eventClass);
        }
        return subscriptionFound;
    }

    private boolean isHandlerForExceptionalEvent(Object handler, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        Class<?> handlerClass = handler.getClass();
        boolean handlementFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlementFound |= isHandlerForExceptionalEventType(handlerClass, clazz);
            }
        } else {
            handlementFound = isHandlerForExceptionalEventType(handlerClass, exceptionalEventClass);
        }
        return handlementFound;
    }

    private boolean isSubscriberForEventType(Class<?> subscriberClass, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.getClass().equals(subscriberClass))
                        return true;
                }
            }
            return false;
        }
    }

    private boolean isHandlerForExceptionalEventType(Class<?> handlerClass, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.getClass().equals(handlerClass))
                        return true;
                }
            }
            return false;
        }
    }

    private boolean isSubscriberClassForEvent(Object subscriber, Object event) {
        Class<?> eventClass = event.getClass();
        Class<?> subscriberClass = subscriber.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= isSubscriberClassForEventType(subscriberClass, clazz);
            }
        } else {
            subscriptionFound = isSubscriberClassForEventType(subscriberClass, eventClass);
        }
        return subscriptionFound;
    }

    private boolean isHandlerClassForExceptionalEvent(Object handler, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        Class<?> handlerClass = handler.getClass();
        boolean handlementFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlementFound |= isHandlerClassForExceptionalEventType(handlerClass, clazz);
            }
        } else {
            handlementFound = isHandlerClassForExceptionalEventType(handlerClass, exceptionalEventClass);
        }
        return handlementFound;
    }

    private boolean isSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = subscriberClassesByEventType.get(eventClass);
            if(subscriberClasses != null && !subscriberClasses.isEmpty()) {
                for(SubscriberClass subscriberClass : subscriberClasses) {
                    if(subscriberClass.subscriberClass.equals(subscriberClassType))
                        return true;
                }
            }
            return false;
        }
    }

    private boolean isHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = handlerClassesByExceptionalEventType.get(exceptionalEventClass);
            if(handlerClasses != null && !handlerClasses.isEmpty()) {
                for(HandlerClass handlerClass : handlerClasses) {
                    if(handlerClass.handlerClass.equals(handlerClassType))
                        return true;
                }
            }
            return false;
        }
    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadPoster != null) {
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeSubscriber(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    private void throwsToHandlement(Handlement handlement, Object exceptionalEvent, boolean isMainThread) {
        switch (handlement.handlerMethod.threadMode) {
            case THROWING:
                invokeHandler(handlement, exceptionalEvent);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeHandler(handlement, exceptionalEvent);
                } else {
                    mainThreadThrower.enqueue(handlement, exceptionalEvent);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadThrower != null) {
                    mainThreadThrower.enqueue(handlement, exceptionalEvent);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeHandler(handlement, exceptionalEvent);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundThrower.enqueue(handlement, exceptionalEvent);
                } else {
                    invokeHandler(handlement, exceptionalEvent);
                }
                break;
            case ASYNC:
                asyncThrower.enqueue(handlement, exceptionalEvent);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + handlement.handlerMethod.threadMode);
        }
    }

    private void prepareLatePostingEvent(Object event) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriberClassFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriberClassFound |= prepareLatePostingEventForEventType(event, clazz);
            }
        } else {
            subscriberClassFound = prepareLatePostingEventForEventType(event, eventClass);
        }
    }

    private void prepareLateThrowingExceptionalEvent(Object exceptionalEvent) throws Error {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean handlerClassFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlerClassFound |= prepareLateThrowingExceptionalEventForExceptionalEventType(exceptionalEvent, clazz);
            }
        } else {
            handlerClassFound = prepareLateThrowingExceptionalEventForExceptionalEventType(exceptionalEvent, exceptionalEventClass);
        }
    }

    private boolean prepareLatePostingEventForEventType(Object event, Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = subscriberClassesByEventType.get(eventClass);
        }
        if (subscriberClasses != null && !subscriberClasses.isEmpty()) {
            for (SubscriberClass subscriberClass : subscriberClasses) {
                Class<?> subscriberClassType = subscriberClass.subscriberClass;
                Intent intent = new Intent(context, subscriberClassType);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                break;
            }
            return true;
        }
        return false;
    }

    private boolean prepareLateThrowingExceptionalEventForExceptionalEventType(Object exceptionalEvent, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = handlerClassesByExceptionalEventType.get(exceptionalEventClass);
        }
        if (handlerClasses != null && !handlerClasses.isEmpty()) {
            for (HandlerClass handlerClass : handlerClasses) {
                if(handlerClass.handlerMethod.actionMode == ExceptionalActionMode.START_AND_HANDLE) {
                    Class<?> handlerClassType = handlerClass.handlerClass;
                    Intent intent = new Intent(context, handlerClassType);
                    context.startActivity(intent);
                    //break;
                }
            }
            return true;
        }
        return false;
    }

    /** Looks up all Class objects including super classes and interfaces. Should also work for interfaces. */
    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /** Looks up all Class objects including super classes and interfaces. Should also work for interfaces. */
    private static List<Class<?>> lookupAllExceptionalEventTypes(Class<?> exceptionalEventClass) {
        synchronized (exceptionalEventTypesCache) {
            List<Class<?>> exceptionalEventTypes = exceptionalEventTypesCache.get(exceptionalEventClass);
            if (exceptionalEventTypes == null) {
                exceptionalEventTypes = new ArrayList<>();
                Class<?> clazz = exceptionalEventClass;
                while (clazz != null) {
                    exceptionalEventTypes.add(clazz);
                    addInterfaces(exceptionalEventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                exceptionalEventTypesCache.put(exceptionalEventClass, exceptionalEventTypes);
            }
            return exceptionalEventTypes;
        }
    }

    /** Recurses through super interfaces. */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregisterSubscriber(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * Invokes the handler if the handlements is still active. Skipping handlements prevents race conditions
     * between {@link #unregisterHandler(Object)} and exceptional event delivery. Otherwise the exceptional event might be delivered after the
     * handler unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    void invokeHandler(PendingThrow pendingThrow) {
        Object exceptionalEvent = pendingThrow.exceptionalEvent;
        Handlement handlement = pendingThrow.handlement;
        PendingThrow.releasePendingThrow(pendingThrow);
        if (handlement.active) {
            invokeHandler(handlement, exceptionalEvent);
        }
    }

    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    void invokeHandler(Handlement handlement, Object exceptionalEvent) {
        try {
            handlement.handlerMethod.method.invoke(handlement.handler, exceptionalEvent);
        } catch (InvocationTargetException e) {
            handleHandlerException(handlement, exceptionalEvent, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                logger.log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                logger.log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    private void handleHandlerException(Handlement handlement, Object exceptionalEvent, Throwable cause) {
        if (exceptionalEvent instanceof HandlerExceptionExceptionalEvent) {
            if (logHandlerExceptions) {
                // Don't send another HandlerExceptionExceptionalEvent to avoid infinite exceptional event recursion, just log.
                logger.log(Level.SEVERE, "HandlerExceptionExceptionalEvent handler " + handlement.handler.getClass()
                        + " threw an exception", cause);
                HandlerExceptionExceptionalEvent exExceptionalEvent = (HandlerExceptionExceptionalEvent) exceptionalEvent;
                logger.log(Level.SEVERE, "Initial exceptional event " + exExceptionalEvent.causingExceptionalEvent + " caused exception in "
                        + exExceptionalEvent.causingHandler, exExceptionalEvent.throwable);
            }
        } else {
            if (throwHandlerException) {
                throw new EventBusException("Invoking handler failed", cause);
            }
            if (logHandlerExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch exceptional event: " + exceptionalEvent.getClass() + " to handling class "
                        + handlement.handler.getClass(), cause);
            }
            if (sendHandlerExceptionExceptionalEvent) {
                HandlerExceptionExceptionalEvent exExceptionalEvent = new HandlerExceptionExceptionalEvent(this, cause, exceptionalEvent,
                        handlement.handler);
                throwsException(exExceptionalEvent);
            }
        }
    }

    /** For ThreadLocal, much faster to set (and get multiple values). */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<>();
        boolean isPosting;
        boolean isMainThread;
        boolean isLate;
        Subscription subscription;
        Object event;
        boolean canceled;

        public PostingThreadState() {
            super();
        }

        public PostingThreadState(boolean isLate) {
            super();
            this.isLate = isLate;
        }
    }

    /** For ThreadLocal, much faster to set (and get multiple values). */
    final static class ThrowingThreadState {
        final List<Object> exceptionalEventQueue = new ArrayList<>();
        boolean isThrowing;
        boolean isMainThread;
        boolean isLate;
        Handlement handlement;
        Object exceptionalEvent;
        boolean canceled;

        public ThrowingThreadState() {
            super();
        }

        public ThrowingThreadState(boolean isLate) {
            super();
            this.isLate = isLate;
        }
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * For internal use only.
     */
    public Logger getLogger() {
        return logger;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    // Just an idea: we could provide a callback to throws() to be notified, an alternative would be exceptional events, of course...
    /* public */interface ThrowsCallback {
        void onThrowsCompleted(List<HandlerExceptionExceptionalEvent> exceptionExceptionalEvents);
    }

    @Override
    public String toString() {
        return "EventBus[indexCount=" + indexCount
                + ", indexCountSubscriber=" + indexCountSubscriber
                + ", indexCountHandler=" + indexCountHandler
                + ", eventInheritance=" + eventInheritance
                + ", exceptionalEventInheritance=" + exceptionalEventInheritance + "]";
    }
}