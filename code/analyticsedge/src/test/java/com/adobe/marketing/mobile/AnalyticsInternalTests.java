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

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.powermock.reflect.internal.WhiteboxImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, ExtensionUnexpectedError.class, PlatformServices.class, Edge.class, Context.class, ExperienceEvent.class, App.class})
public class AnalyticsInternalTests {

    private AnalyticsInternal analyticsInternal;

    // Mocks
    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    ExtensionUnexpectedError mockExtensionUnexpectedError;
    @Mock
    Context context;

    @Before
    public void setup() {
        PowerMockito.mockStatic(Edge.class);
        PowerMockito.mockStatic(ExperienceEvent.class);
        PowerMockito.mockStatic(App.class);
        Mockito.when(App.getAppContext()).thenReturn(context);
        analyticsInternal = new AnalyticsInternal(mockExtensionApi);
    }

    private void setPrivacyStatus(final String privacyStatus) {
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, privacyStatus);
            }
        };
        Event configEvent = new Event.Builder("config event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT).setEventData(configData).build();
        analyticsInternal.processConfigurationResponse(configEvent);
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
        String moduleName = analyticsInternal.getName();
        assertEquals("getName should return the correct module name", AnalyticsConstants.EXTENSION_NAME, moduleName);
    }

    // ========================================================================================
    // getVersion
    // ========================================================================================
    @Test
    public void test_getVersion() {
        // test
        String moduleVersion = analyticsInternal.getVersion();
        assertEquals("getVesion should return the correct module version", AnalyticsConstants.EXTENSION_VERSION,
                moduleVersion);
    }

    // ========================================================================================
    // onUnexpectedError
    // ========================================================================================
    @Test
    public void test_onUnexpectedError() {
        // test
        analyticsInternal.onUnexpectedError(mockExtensionUnexpectedError);
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // onUnregistered
    // ========================================================================================

    @Test
    public void test_onUnregistered() {
        // test
        analyticsInternal.onUnregistered();
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // queueEvent
    // ========================================================================================
    @Test
    public void test_QueueEvent() {
        // test 1
        assertNotNull("EventQueue instance is should never be null", analyticsInternal.getEventQueue());

        // test 2
        Event sampleEvent = new Event.Builder("event 1", "eventType", "eventSource").build();
        analyticsInternal.queueEvent(sampleEvent);
        assertEquals("The size of the eventQueue should be correct", 1, analyticsInternal.getEventQueue().size());

        // test 3
        analyticsInternal.queueEvent(null);
        assertEquals("The size of the eventQueue should be correct", 1, analyticsInternal.getEventQueue().size());

        // test 4
        Event anotherEvent = new Event.Builder("event 2", "eventType", "eventSource").build();
        analyticsInternal.queueEvent(anotherEvent);
        assertEquals("The size of the eventQueue should be correct", 2, analyticsInternal.getEventQueue().size());
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
        analyticsInternal.processEvents();

        // verify
        verify(mockExtensionApi, times(0)).getSharedEventState(AnalyticsConstants.SharedStateKeys.CONFIGURATION, mockEvent, mockCallback);
    }

    @Test
    public void test_processEvents_when_handlingGenericTrackEvent() {
        // setup
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).build();

        // test
        analyticsInternal.queueEvent(sampleEvent);
        analyticsInternal.processEvents();

        // verify
        PowerMockito.verifyStatic(Edge.class, times(1));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_processEvents_when_handlingConfigurationResponseEvent() {
        // setup
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");
            }
        };
        Event sampleEvent = new Event.Builder("config event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT).setEventData(configData).build();
        // queue a track event
        analyticsInternal.queueEvent(new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).build());
        // test
        analyticsInternal.queueEvent(sampleEvent);
        analyticsInternal.processEvents();

        // verify
        PowerMockito.verifyStatic(Edge.class, times(1));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    // ========================================================================================
    // getExecutor
    // ========================================================================================
    @Test
    public void test_getExecutor_NeverReturnsNull() {
        // test
        ExecutorService executorService = analyticsInternal.getExecutor();
        assertNotNull("The executor should not return null", executorService);

        // verify
        assertEquals("Gets the same executor instance on the next get", executorService, analyticsInternal.getExecutor());
    }

    // ========================================================================================
    // handleGenericTrack
    // ========================================================================================
    @Test
    public void test_handleGenericTrack_Smoke() {
        // setup
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).build();

        // test
        analyticsInternal.handleGenericTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(1));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_handleGenericTrack_NullTrackEvent() {
        // setup
        Event sampleEvent = null;

        // test
        analyticsInternal.handleGenericTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_handleGenericTrack_EventNotATrackEvent() {
        // setup
        Event sampleEvent = new Event.Builder("random event", EventType.GENERIC_DATA, EventSource.REQUEST_CONTENT).build();

        // test
        analyticsInternal.handleGenericTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_handleGenericTrack_WhenPrivacyOptedOut() {
        // setup
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventNumber(1).build();
        // setup privacy status
        setPrivacyStatus("optedout");

        // test
        analyticsInternal.handleGenericTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    // ========================================================================================
    // processAnalyticsVars
    // ========================================================================================
    @Test
    public void test_processAnalyticsVars_trackActionInternalTrue() {
        // setup
        HashMap<String,String> processedVars = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, true);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();
        String eventTimestamp = Long.toString(sampleEvent.getTimestampInSeconds());

        // test
        try {
            processedVars = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsVars", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsVars: " + e.getMessage());
        }

        // verify
        assertEquals("UTF-8", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals(AnalyticsConstants.IGNORE_PAGE_NAME_VALUE, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals(AnalyticsConstants.INTERNAL_ACTION_PREFIX+"action", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals(AnalyticsConstants.APP_STATE_FOREGROUND, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(eventTimestamp, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
    }

    @Test
    public void test_processAnalyticsVars_trackActionInternalFalse() {
        // setup
        HashMap<String,String> processedVars = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, false);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();
        String eventTimestamp = Long.toString(sampleEvent.getTimestampInSeconds());

        // test
        try {
            processedVars = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsVars", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsVars: " + e.getMessage());
        }

        // verify
        assertEquals("UTF-8", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals(AnalyticsConstants.IGNORE_PAGE_NAME_VALUE, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals(AnalyticsConstants.ACTION_PREFIX+"action", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals(AnalyticsConstants.APP_STATE_FOREGROUND, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(eventTimestamp, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
    }

    @Test
    public void test_processAnalyticsVars_trackState() {
        // setup
        HashMap<String,String> processedVars = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_STATE, "state");
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();
        String eventTimestamp = Long.toString(sampleEvent.getTimestampInSeconds());

        // test
        try {
            processedVars = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsVars", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsVars: " + e.getMessage());
        }

        // verify
        assertEquals("UTF-8", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("state", processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME));
        assertEquals(AnalyticsConstants.APP_STATE_FOREGROUND, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(eventTimestamp, processedVars.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
    }

    // ========================================================================================
    // processAnalyticsData
    // ========================================================================================
    @Test
    public void test_processAnalyticsData_trackActionInternalTrue() {
        // setup
        HashMap<String,String> processedData = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, true);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();

        // test
        try {
            processedData = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsData", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsData: " + e.getMessage());
        }

        // verify
        assertEquals("action", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION));
        assertEquals("unknown", processedData.get(AnalyticsConstants.AnalyticsRequestKeys.PRIVACY_MODE));
        assertEquals("true", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL));
        assertEquals("action", processedData.get(AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION_KEY));
    }

    @Test
    public void test_processAnalyticsData_trackActionInternalFalse() {
        // setup
        HashMap<String,String> processedData = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, false);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();

        // test
        try {
            processedData = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsData", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsData: " + e.getMessage());
        }

        // verify
        assertEquals("action", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION));
        assertEquals("unknown", processedData.get(AnalyticsConstants.AnalyticsRequestKeys.PRIVACY_MODE));
        assertEquals("false", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL));
        assertEquals("action", processedData.get(AnalyticsConstants.ContextDataKeys.ACTION_KEY));
    }

    @Test
    public void test_processAnalyticsData_trackActionPrivacyOptedIn() {
        // setup
        HashMap<String,String> processedData = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, false);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).setEventNumber(1).build();
        // setup privacy status
        setPrivacyStatus("optedin");

        // test
        try {
            processedData = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsData", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsData: " + e.getMessage());
        }

        // verify
        assertEquals("action", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION));
        assertEquals("false", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL));
        assertEquals("action", processedData.get(AnalyticsConstants.ContextDataKeys.ACTION_KEY));
    }

    @Test
    public void test_processAnalyticsData_trackState() {
        // setup
        HashMap<String,String> processedData = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_STATE, "state");
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).build();

        // test
        try {
            processedData = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsData", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsData: " + e.getMessage());
        }

        // verify
        assertEquals(processedData.get(AnalyticsConstants.EventDataKeys.TRACK_STATE), "state");
        assertEquals(processedData.get(AnalyticsConstants.AnalyticsRequestKeys.PRIVACY_MODE), "unknown");
    }

    @Test
    public void test_processAnalyticsData_trackStatePrivacyOptedIn() {
        // setup
        HashMap<String,String> processedData = new HashMap<>();
        HashMap<String, Object> analyticsVars = new HashMap<>();
        analyticsVars.put(AnalyticsConstants.EventDataKeys.TRACK_STATE, "state");
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventData(analyticsVars).setEventNumber(1).build();
        // setup privacy status
        setPrivacyStatus("optedin");

        // test
        try {
            processedData = WhiteboxImpl.invokeMethod(analyticsInternal, "processAnalyticsData", sampleEvent);
        } catch (Exception e){
            fail("Exception when invoking processAnalyticsData: " + e.getMessage());
        }

        // verify
        assertEquals("state", processedData.get(AnalyticsConstants.EventDataKeys.TRACK_STATE));
    }
}