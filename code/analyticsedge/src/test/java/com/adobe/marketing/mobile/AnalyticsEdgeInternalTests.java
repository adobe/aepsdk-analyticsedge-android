/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

// TODO: placeholder, needs to be cleaned
package com.adobe.marketing.mobile;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, ExtensionUnexpectedError.class, AnalyticsEdgeState.class, PlatformServices.class, LocalStorageService.class, Edge.class, ExperienceEvent.class, App.class, Context.class})
public class AnalyticsEdgeInternalTests {

    private int EXECUTOR_TIMEOUT = 5;
    private AnalyticsEdgeInternal analyticsEdgeInternal;

    // Mocks
    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    ExtensionUnexpectedError mockExtensionUnexpectedError;
    @Mock
    AnalyticsEdgeState analyticsEdgeState;
    @Mock
    PlatformServices mockPlatformServices;
    @Mock
    LocalStorageService mockLocalStorageService;
    @Mock
    NetworkService mockNetworkService;
    @Mock
    Map<String, Object> mockConfigData;
    @Mock
    ConcurrentLinkedQueue<Event> mockEventQueue;
    @Mock
    Application mockApplication;
    @Mock
    Context context;

    @Before
    public void setup() {
        PowerMockito.mockStatic(Edge.class);
        PowerMockito.mockStatic(ExperienceEvent.class);
        PowerMockito.mockStatic(App.class);
        Mockito.when(App.getAppContext()).thenReturn(context);
        analyticsEdgeInternal = new AnalyticsEdgeInternal(mockExtensionApi);
    }

    // ========================================================================================
    // constructor
    // ========================================================================================
    @Test
    public void test_Constructor() {
        // verify 2 listeners are registered
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.CONFIGURATION),
                eq(EventSource.RESPONSE_CONTENT), eq(ConfigurationResponseContentListener.class));
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.GENERIC_TRACK),
                eq(EventSource.REQUEST_CONTENT), eq(GenericTrackRequestContentListener.class));
    }

    // ========================================================================================
    // getName
    // ========================================================================================
    @Test
    public void test_getName() {
        // test
        String moduleName = analyticsEdgeInternal.getName();
        assertEquals("getName should return the correct module name", AnalyticsEdgeConstants.EXTENSION_NAME, moduleName);
    }

    // ========================================================================================
    // getVersion
    // ========================================================================================
    @Test
    public void test_getVersion() {
        // test
        String moduleVersion = analyticsEdgeInternal.getVersion();
        assertEquals("getVesion should return the correct module version", AnalyticsEdgeConstants.EXTENSION_VERSION,
                moduleVersion);
    }

    // ========================================================================================
    // onUnexpectedError
    // ========================================================================================
    @Test
    public void test_onUnexpectedError() {
        // test
        analyticsEdgeInternal.onUnexpectedError(mockExtensionUnexpectedError);
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // onUnregistered
    // ========================================================================================

    @Test
    public void test_onUnregistered() {
        // test
        analyticsEdgeInternal.onUnregistered();
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // queueEvent
    // ========================================================================================
    @Test
    public void test_QueueEvent() {
        // test 1
        assertNotNull("EventQueue instance is should never be null", analyticsEdgeInternal.getEventQueue());

        // test 2
        Event sampleEvent = new Event.Builder("event 1", "eventType", "eventSource").build();
        analyticsEdgeInternal.queueEvent(sampleEvent);
        assertEquals("The size of the eventQueue should be correct", 1, analyticsEdgeInternal.getEventQueue().size());

        // test 3
        analyticsEdgeInternal.queueEvent(null);
        assertEquals("The size of the eventQueue should be correct", 1, analyticsEdgeInternal.getEventQueue().size());

        // test 4
        Event anotherEvent = new Event.Builder("event 2", "eventType", "eventSource").build();
        analyticsEdgeInternal.queueEvent(anotherEvent);
        assertEquals("The size of the eventQueue should be correct", 2, analyticsEdgeInternal.getEventQueue().size());
    }

    // ========================================================================================
    // processEvents
    // ========================================================================================
    @Test
    public void test_processEvents_when_noEventInQueue() {
        // Mocks
        ExtensionErrorCallback<ExtensionError> mockCallback = new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {

            }
        };
        Event mockEvent = new Event.Builder("event 2", "eventType", "eventSource").build();

        // test
        analyticsEdgeInternal.processEvents();

        // verify
        verify(mockExtensionApi, times(0)).getSharedEventState(AnalyticsEdgeConstants.EventDataKeys.Configuration.EXTENSION_NAME, mockEvent, mockCallback);
    }

    // ========================================================================================
    // getExecutor
    // ========================================================================================
    @Test
    public void test_getExecutor_NeverReturnsNull() {
        // test
        ExecutorService executorService = analyticsEdgeInternal.getExecutor();
        assertNotNull("The executor should not return null", executorService);

        // verify
        assertEquals("Gets the same executor instance on the next get", executorService, analyticsEdgeInternal.getExecutor());
    }
}