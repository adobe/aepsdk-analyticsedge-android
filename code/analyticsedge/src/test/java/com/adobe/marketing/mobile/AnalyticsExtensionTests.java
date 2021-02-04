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

package com.adobe.marketing.mobile;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, ExtensionUnexpectedError.class, PlatformServices.class, Edge.class, Context.class, ExperienceEvent.class, App.class, MobileCore.class})
public class AnalyticsExtensionTests {

    private AnalyticsExtension analyticsExtension;
    private FakeSystemInfoService fakeSystemInfoService;

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
        fakeSystemInfoService = new FakeSystemInfoService();
        fakeSystemInfoService.setApplicationName("testAppName");
        fakeSystemInfoService.setApplicationVersion("1.0.0");
        fakeSystemInfoService.setApplicationVersionCode("12345");
        analyticsExtension = new AnalyticsExtension(mockExtensionApi, fakeSystemInfoService);
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
        // verify 3 listeners are registered
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.CONFIGURATION),
                eq(EventSource.RESPONSE_CONTENT), eq(ConfigurationResponseContentListener.class));
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.GENERIC_TRACK),
                eq(EventSource.REQUEST_CONTENT), eq(GenericTrackRequestContentListener.class));
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.RULES_ENGINE),
                eq(EventSource.RESPONSE_CONTENT), eq(RulesEngineResponseContentListener.class));
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
    public void test_handleAnalyticsTrackActionEvent() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("AMACTION:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("value1", edgeEventAnalyticsContextData.get("key1"));
        assertEquals("value2", edgeEventAnalyticsContextData.get("key2"));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.ACTION));
    }

    @Test
    public void test_handleAnalyticsTrackStateEvent() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_STATE, "state");
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("state", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("value1", edgeEventAnalyticsContextData.get("key1"));
        assertEquals("value2", edgeEventAnalyticsContextData.get("key2"));
        assertNull(edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.ACTION));
    }

    @Test
    public void test_handleAnalyticsTrackInternalEvent() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

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
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("ADBINTERNAL:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("value1", edgeEventAnalyticsContextData.get("key1"));
        assertEquals("value2", edgeEventAnalyticsContextData.get("key2"));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION));
    }

    @Test
    public void test_handleAnalyticsTrackActionEvent_WithEmptyContextData() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("AMACTION:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.ACTION));
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

    @Test
    public void test_handleAnalyticsTrackEvent_WhenPrivacyStatusIsNull() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        eventData.putBoolean(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, true);
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState(null);
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("ADBINTERNAL:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("value1", edgeEventAnalyticsContextData.get("key1"));
        assertEquals("value2", edgeEventAnalyticsContextData.get("key2"));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION));
    }

    @Test
    public void test_handleAnalyticsTrack_ContextDataStripped() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("&&product", "value");
        EventData eventData = new EventData();
        eventData.putString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, "action");
        eventData.putStringMap(AnalyticsConstants.EventDataKeys.CONTEXT_DATA, contextData);
        Event sampleEvent = new Event.Builder("generic track", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("lnk_o", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME));
        assertEquals("AMACTION:action", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals("value", edgeEventAnalyticsData.get("product"));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("action", edgeEventAnalyticsContextData.get(AnalyticsConstants.ContextDataKeys.ACTION));
    }

    // ========================================================================================
    // handleRulesEngineEvent
    // ========================================================================================
    @Test
    public void test_handleRulesEngineTrackEvent_Smoke() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        HashMap<String, Object> detailMap = new HashMap<>();
        detailMap.put("contextdata", contextData);
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.ID, "id");
        consequence.put(AnalyticsConstants.EventDataKeys.TYPE, "an");
        consequence.put(AnalyticsConstants.EventDataKeys.DETAIL, detailMap);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getName().equalsIgnoreCase(AnalyticsConstants.ANALYTICS_XDM_EVENTNAME));
        Assert.assertTrue(event.getType().equalsIgnoreCase(AnalyticsConstants.Edge.EVENT_TYPE));
        Assert.assertTrue(event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT.getName()));

        Map<String, Object> capturedEventData = event.getEventData();
        Map<String, String> xdm = (Map<String, String>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.XDM);

        Map<String, Object> edgeEventData = (Map<String, Object>) capturedEventData.get(AnalyticsConstants.XDMDataKeys.DATA);
        Map<String, Object> edgeLegacyData = (Map<String, Object>) edgeEventData.get(AnalyticsConstants.XDMDataKeys.LEGACY);
        HashMap edgeEventAnalyticsData = (HashMap)edgeLegacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS);
        HashMap edgeEventAnalyticsContextData = (HashMap)edgeEventAnalyticsData.get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);
        assertEquals("legacy.analytics", xdm.get(AnalyticsConstants.XDMDataKeys.EVENTTYPE));
        assertEquals(1, edgeEventAnalyticsData.get("ndh"));
        assertEquals("UTF-8", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CHARSET));
        assertEquals(AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP));
        assertEquals("testAppName1.0.012345", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME));
        assertEquals("foreground", edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE));
        assertEquals(timestamp, edgeEventAnalyticsData.get(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP));
        assertEquals("value1", edgeEventAnalyticsContextData.get("key1"));
        assertEquals("value2", edgeEventAnalyticsContextData.get("key2"));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_MissingIdInConsequence() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        HashMap<String, Object> detailMap = new HashMap<>();
        detailMap.put("contextdata", contextData);
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.TYPE, "an");
        consequence.put(AnalyticsConstants.EventDataKeys.DETAIL, detailMap);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_MissingTypeInConsequence() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        HashMap<String, Object> detailMap = new HashMap<>();
        detailMap.put("contextdata", contextData);
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.ID, "id");
        consequence.put(AnalyticsConstants.EventDataKeys.DETAIL, detailMap);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_IncorrectTypeInConsequence() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        HashMap<String, Object> detailMap = new HashMap<>();
        detailMap.put("contextdata", contextData);
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.ID, "id");
        consequence.put(AnalyticsConstants.EventDataKeys.TYPE, "invalid");
        consequence.put(AnalyticsConstants.EventDataKeys.DETAIL, detailMap);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_NoEventData() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_NullEvent() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        Event sampleEvent = null;
        setupPrivacyStatusInSharedState("optedin");

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_NullConsequence() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, null);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
    }

    @Test
    public void test_handleRulesEngineTrackEvent_MissingConsequenceDetail() {
        //setup MobileCore mock method
        PowerMockito.mockStatic(MobileCore.class);

        // setup
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.TYPE, "an");
        consequence.put(AnalyticsConstants.EventDataKeys.ID, "id");
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();
        setupPrivacyStatusInSharedState("optedin");
        String timestamp = String.valueOf(sampleEvent.getTimestampInSeconds());

        // test
        analyticsExtension.handleRulesEngineEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(0));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
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

    // ========================================================================================
    // Assurance Debug Session
    // ========================================================================================

    @Test
    public void test_analyticsContextDataShouldContainEventIdentifier() {
        //Mocking static methods of MobileCore
        PowerMockito.mockStatic(MobileCore.class);

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

        // Mocking Assurance shared state
        String eventUuid = sampleEvent.getUniqueIdentifier();
        EventData mockEventData = new EventData();
        mockEventData.putString(AnalyticsConstants.EventDataKeys.SESSION_ID, "session_id");
        Mockito.when(mockExtensionApi.getSharedEventState(AnalyticsConstants.SharedStateKeys.ASSURANCE, sampleEvent)).thenReturn(mockEventData);

        // test
        analyticsExtension.handleAnalyticsTrackEvent(sampleEvent);

        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getEventData().containsKey(AnalyticsConstants.XDMDataKeys.DATA));
        Map<String, Object> legacyData = (Map<String, Object>) ((Map<String, Object>)event.getEventData().get(AnalyticsConstants.XDMDataKeys.DATA)).get(AnalyticsConstants.XDMDataKeys.LEGACY);
        // Assertion for Assurance debug session
        String assuranceDebugId = (String) ((Map<String, Object>)((Map<String, Object>)legacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS)).get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA)).get(AnalyticsConstants.ContextDataKeys.EVENT_IDENTIFIER);
        assertEquals(assuranceDebugId, eventUuid);
    }

    @Test
    public void test_analyticsContextDataShouldNotContainEventIdentifier() {
        //Mocking static methods of MobileCore
        PowerMockito.mockStatic(MobileCore.class);

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
        // verify
        ArgumentCaptor<Event> argument = ArgumentCaptor.forClass(Event.class);
        PowerMockito.verifyStatic(MobileCore.class, times(1));
        MobileCore.dispatchEvent(argument.capture(), (ExtensionErrorCallback<ExtensionError>) eq(null));
        Assert.assertTrue(argument.getValue() instanceof Event);
        Event event = argument.getValue();
        Assert.assertTrue(event.getEventData().containsKey(AnalyticsConstants.XDMDataKeys.DATA));
        Map<String, Object> legacyData = (Map<String, Object>) ((Map<String, Object>)event.getEventData().get(AnalyticsConstants.XDMDataKeys.DATA)).get(AnalyticsConstants.XDMDataKeys.LEGACY);
        // Assertion for Assurance debug session
        Map<String, Object> analyticsContextData = (Map<String, Object>)((Map<String, Object>)legacyData.get(AnalyticsConstants.XDMDataKeys.ANALYTICS)).get(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA);

        // Assertion for Assurance debug session
        assertFalse(analyticsContextData.containsKey(AnalyticsConstants.ContextDataKeys.EVENT_IDENTIFIER));
    }
}