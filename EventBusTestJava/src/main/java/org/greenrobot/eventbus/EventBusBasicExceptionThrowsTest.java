/*
 * Copyright (C) 2012-2017 Markus Junginger, greenrobot (http://greenrobot.org)
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Markus Junginger, greenrobot
 */
@SuppressWarnings({"WeakerAccess", "UnusedParameters", "unused"})
public class EventBusBasicExceptionThrowsTest extends AbstractEventBusTest {

    public static class WithIndex extends EventBusBasicExceptionThrowsTest {
        @Test
        public void dummy() {
        }

    }

    private String lastStringEvent;
    private int countStringEvent;
    private int countIntEvent;
    private int lastIntEvent;
    private int countMyEventExtended;
    private int countMyEvent;
    private int countMyEvent2;

    @Test
    public void testRegisterAndThrowsException() {
        // Use an activity to test real life performance
        StringThrowsExceptionHandler stringThrowsExceptionHandler = new StringThrowsExceptionHandler();
        String exception = "Hello";

        long start = System.currentTimeMillis();
        eventBus.registerHandler(stringThrowsExceptionHandler);
        long time = System.currentTimeMillis() - start;
        log("Registered in " + time + "ms");

        eventBus.throwsException(exception);

        assertEquals(exception, stringThrowsExceptionHandler.lastStringThrowsException);
    }

    @Test
    public void testThrowsExceptionWithoutHandler() {
        eventBus.throwsException("Hello");
    }

    @Test
    public void testUnregisterWithoutRegister() {
        // Results in a warning without throwing
        eventBus.unregisterHandler(this);
    }


    // This will throw "out of memory" if handlers are leaked
    @Test
    public void testUnregisterNotLeaking() {
        int heapMBytes = (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L));
        for (int i = 0; i < heapMBytes * 2; i++) {
            @SuppressWarnings("unused")
            EventBusBasicExceptionThrowsTest exceptionHandler = new EventBusBasicExceptionThrowsTest() {
                byte[] expensiveObject = new byte[1024 * 1024];
            };
            eventBus.registerHandler(exceptionHandler);
            eventBus.unregisterHandler(exceptionHandler);
            log("Iteration " + i + " / max heap: " + heapMBytes);
        }
    }

    @Test
    public void testRegisterTwice() {
        eventBus.registerHandler(this);
        try {
            eventBus.registerHandler(this);
            fail("Did not throw");
        } catch (RuntimeException expected) {
            // OK
        }
    }

    @Test
    public void testIsHandlerRegistered() {
        assertFalse(eventBus.isHandlerRegistered(this));
        eventBus.registerHandler(this);
        assertTrue(eventBus.isHandlerRegistered(this));
        eventBus.unregisterHandler(this);
        assertFalse(eventBus.isHandlerRegistered(this));
    }

    @Test
    public void testThrowsExceptionWithTwoHandler() {
        EventBusBasicExceptionThrowsTest test2 = new EventBusBasicExceptionThrowsTest();
        eventBus.registerHandler(this);
        eventBus.registerHandler(test2);
        String event = "Hello";
        eventBus.throwsException(event);
        assertEquals(event, lastStringEvent);
        assertEquals(event, test2.lastStringEvent);
    }

    @Test
    public void testThrowsExceptionMultipleTimes() {
        eventBus.registerHandler(this);
        MyEvent event = new MyEvent();
        int count = 1000;
        long start = System.currentTimeMillis();
        // Debug.startMethodTracing("testPostMultipleTimes" + count);
        for (int i = 0; i < count; i++) {
            eventBus.throwsException(event);
        }
        // Debug.stopMethodTracing();
        long time = System.currentTimeMillis() - start;
        log("Posted " + count + " events in " + time + "ms");
        assertEquals(count, countMyEvent);
    }

    @Test
    public void testMultipleHandlerMethodsForEvent() {
        eventBus.registerHandler(this);
        MyEvent event = new MyEvent();
        eventBus.throwsException(event);
        assertEquals(1, countMyEvent);
        assertEquals(1, countMyEvent2);
    }

    @Test
    public void testThrowsExceptionAfterUnregisterHandler() {
        eventBus.registerHandler(this);
        eventBus.unregisterHandler(this);
        eventBus.throwsException("Hello");
        assertNull(lastStringEvent);
    }

    @Test
    public void testRegisterAndThrowsExceptionTwoTypes() {
        eventBus.registerHandler(this);
        eventBus.throwsException(42);
        eventBus.throwsException("Hello");
        assertEquals(1, countIntEvent);
        assertEquals(1, countStringEvent);
        assertEquals(42, lastIntEvent);
        assertEquals("Hello", lastStringEvent);
    }

    @Test
    public void testRegisterUnregisterAndThrowsExceptionTwoTypes() {
        eventBus.registerHandler(this);
        eventBus.unregisterHandler(this);
        eventBus.throwsException(42);
        eventBus.throwsException("Hello");
        assertEquals(0, countIntEvent);
        assertEquals(0, lastIntEvent);
        assertEquals(0, countStringEvent);
    }

    @Test
    public void testThrowsExceptionOnDifferentEventBus() {
        eventBus.registerHandler(this);
        new EventBus().throwsException("Hello");
        assertEquals(0, countStringEvent);
    }

    @Test
    public void testThrowsExceptionInEventHandler() {
        RepostInteger reposter = new RepostInteger();
        eventBus.registerHandler(reposter);
        eventBus.registerHandler(this);
        eventBus.throwsException(1);
        assertEquals(10, countIntEvent);
        assertEquals(10, lastIntEvent);
        assertEquals(10, reposter.countEvent);
        assertEquals(10, reposter.lastEvent);
    }

    @Test
    public void testHasHandlerForEvent() {
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));

        eventBus.registerHandler(this);
        assertTrue(eventBus.hasHandlerForExceptionalEventClass(String.class));

        eventBus.unregisterHandler(this);
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));
    }

    @Test
    public void testHasHandlerForEventSuperclass() {
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));

        Object objectHandler = new ObjectHandler();
        eventBus.registerHandler(objectHandler);
        assertTrue(eventBus.hasHandlerForExceptionalEventClass(String.class));

        eventBus.unregisterHandler(objectHandler);
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));
    }

    @Test
    public void testHasHandlerForEventImplementedInterface() {
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));

        Object handler = new CharSequenceHandler();
        eventBus.registerHandler(handler);
        assertTrue(eventBus.hasHandlerForExceptionalEventClass(CharSequence.class));
        assertTrue(eventBus.hasHandlerForExceptionalEventClass(String.class));

        eventBus.unregisterHandler(handler);
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(CharSequence.class));
        assertFalse(eventBus.hasHandlerForExceptionalEventClass(String.class));
    }

    @Handle
    public void onEvent(String event) {
        lastStringEvent = event;
        countStringEvent++;
    }

    @Handle
    public void onEvent(Integer event) {
        lastIntEvent = event;
        countIntEvent++;
    }

    @Handle
    public void onEvent(MyEvent event) {
        countMyEvent++;
    }

    @Handle
    public void onEvent2(MyEvent event) {
        countMyEvent2++;
    }

    @Handle
    public void onEvent(MyEventExtended event) {
        countMyEventExtended++;
    }

    public static class StringThrowsExceptionHandler {
        public String lastStringThrowsException;

        @Handle
        public void onThrowsException(String exception) {
            lastStringThrowsException = exception;
        }
    }

    public static class CharSequenceHandler {
        @Handle
        public void onThrowsException(CharSequence event) {
        }
    }

    public static class ObjectHandler {
        @Handle
        public void onThrowsException(Object event) {
        }
    }

    public class MyEvent {
    }

    public class MyEventExtended extends MyEvent {
    }

    public class RepostInteger {
        public int lastEvent;
        public int countEvent;

        @Handle
        public void onEvent(Integer event) {
            lastEvent = event;
            countEvent++;
            assertEquals(countEvent, event.intValue());

            if (event < 10) {
                int countIntEventBefore = countEvent;
                eventBus.throwsException(event + 1);
                // All our throwsException calls will just enqueue the event, so check count is unchanged
                assertEquals(countIntEventBefore, countIntEventBefore);
            }
        }
    }

}
