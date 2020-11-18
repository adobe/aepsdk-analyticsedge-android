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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, ExtensionUnexpectedError.class, PlatformServices.class, Edge.class, Context.class, ExperienceEvent.class, App.class})
public class AnalyticsExtensionTests {

    private AnalyticsExtension analyticsExtension;

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
        analyticsExtension = new AnalyticsExtension(mockExtensionApi);
    }

    private void setupPrivacyStatusInSharedState(final String privacyStatus) {
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, privacyStatus);
            }
        };
        when(mockExtensionApi.getSharedEventState(anyString(), any(Event.class),
                (ExtensionErrorCallback) eq(null))).thenReturn(configData);
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
        String moduleName = analyticsExtension.getName();
        assertEquals("getName should return the correct module name", AnalyticsConstants.EXTENSION_NAME, moduleName);
    }

    // ========================================================================================
    // getVersion
    // ========================================================================================
    @Test
    public void test_getVersion() {
        // test
        String moduleVersion = analyticsExtension.getVersion();
        assertEquals("getVesion should return the correct module version", AnalyticsConstants.EXTENSION_VERSION,
                moduleVersion);
    }

    // ========================================================================================
    // onUnexpectedError
    // ========================================================================================
    @Test
    public void test_onUnexpectedError() {
        // test
        analyticsExtension.onUnexpectedError(mockExtensionUnexpectedError);
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // onUnregistered
    // ========================================================================================

    @Test
    public void test_onUnregistered() {
        // test
        analyticsExtension.onUnregistered();
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // getExecutor
    // ========================================================================================
    @Test
    public void test_getExecutor_NeverReturnsNull() {
        // test
        ExecutorService executorService = analyticsExtension.getExecutor();
        assertNotNull("The executor should not return null", executorService);

        // verify
        assertEquals("Gets the same executor instance on the next get", executorService, analyticsExtension.getExecutor());
    }

    // ========================================================================================
    // handleAnalyticsTrackEvent
    // ========================================================================================
    @Test
    public void test_handleAnalyticsTrackEvent_Smoke() {
        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        eventData.putBoolean(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, true);
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<ExperienceEvent> argument = ArgumentCaptor.forClass(ExperienceEvent.class);
        PowerMockito.verifyStatic(Edge.class, times(1));
        Edge.sendEvent(argument.capture(), (EdgeCallback) eq(null));
        ExperienceEvent capturedEvent = argument.getValue();
        Map<String, Object> xdmSchema = capturedEvent.getXdmSchema();
        HashMap edgeEventData = (HashMap)capturedEvent.getData().get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeEventData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdmSchema.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("ADBINTERNAL:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION));
        assertEquals("true", edgeEventAnalyticsContextData.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL));
        assertEquals("{key1=value1, key2=value2}", edgeEventAnalyticsContextData.get(AnalyticsConstants.EventDataKeys.CONTEXT_DATA));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION_KEY));
    }

    @Test
    public void test_handleAnalyticsTrackEvent_NullTrackEvent() {
        // setup
        Event sampleEvent = null;

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_handleAnalyticsTrackEvent_EventNotATrackEvent() {
        // setup
        Event sampleEvent = new Event.Builder("random event", EventType.GENERIC_DATA, EventSource.REQUEST_CONTENT).build();

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    @Test
    public void test_handleAnalyticsTrackEvent_WhenPrivacyOptedOut() {
        // setup
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setEventNumber(1).build();
        // setup privacy status
        setupPrivacyStatusInSharedState("optedout");

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        PowerMockito.verifyStatic(Edge.class, times(0));
        Edge.sendEvent(any(ExperienceEvent.class), (EdgeCallback) eq(null));
    }

    // ========================================================================================
    // handleConfigurationEvent
    // ========================================================================================
    @Test
    public void test_handleConfigurationEvent_Smoke() {
        // setup
        setupPrivacyStatusInSharedState("optedin");
        MobilePrivacyStatus privacyStatus = null;
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");
            }
        };
        Event sampleEvent = new Event.Builder("config event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT).setEventData(configData).build();

        // test
        analyticsExtension.handleConfigurationEvent(sampleEvent);

        // verify
        try {
            privacyStatus = WhiteboxImpl.invokeMethod(analyticsExtension, "getPrivacyStatus");
        } catch (Exception e){
            fail("Exception when invoking getPrivacyStatus: " + e.getMessage());
        }
        assertEquals(MobilePrivacyStatus.OPT_IN, privacyStatus);
    }

    @Test
    public void test_handleConfigurationEvent_NullConfigEvent() {
        // setup
        MobilePrivacyStatus privacyStatus = null;

        // test
        analyticsExtension.handleConfigurationEvent(null);

        // verify
        try {
            privacyStatus = WhiteboxImpl.invokeMethod(analyticsExtension, "getPrivacyStatus");
        } catch (Exception e){
            fail("Exception when invoking getPrivacyStatus: " + e.getMessage());
        }
        assertEquals(MobilePrivacyStatus.UNKNOWN, privacyStatus);
    }
}