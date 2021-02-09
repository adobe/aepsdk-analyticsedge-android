/*
 Copyright 2021 Adobe. All rights reserved.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, App.class, Context.class, AnalyticsExtension.class})

public class RulesEngineResponseContentListenerTests {

    private RulesEngineResponseContentListener rulesEngineResponseContentListener;
    private int EXECUTOR_TIMEOUT = 5;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Mocks
    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    Context context;
    @Mock
    AnalyticsExtension mockAnalyticsExtension;

    @Before
    public void setup() {
        PowerMockito.mockStatic(App.class);
        Mockito.when(App.getAppContext()).thenReturn(context);
    }

    @Before
    public void beforeEach() {
        rulesEngineResponseContentListener = new RulesEngineResponseContentListener(mockExtensionApi, EventType.RULES_ENGINE.getName(), EventSource.RESPONSE_CONTENT.getName());
        when(mockAnalyticsExtension.getExecutor()).thenReturn(executor);
        when(mockExtensionApi.getExtension()).thenReturn(mockAnalyticsExtension);
    }

    @Test
    public void test_validRulesEngineResponseContentEvent() {
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
        HashMap<String, Object> triggeredConsequence = new HashMap<>();
        triggeredConsequence.put(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();

        // test
        rulesEngineResponseContentListener.hear(sampleEvent);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsExtension, times(1)).handleRulesEngineEvent(sampleEvent);
    }

    @Test
    public void test_nullRulesEngineResponseContentEvent() {
        // test
        rulesEngineResponseContentListener.hear(null);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsExtension, times(0)).handleRulesEngineEvent(null);
    }

    @Test
    public void test_hearRulesEngineResponseContentEventWhenParentExtensionIsNull() {
        // setup
        when(mockExtensionApi.getExtension()).thenReturn(null);
        HashMap<String, String> contextData = new HashMap<>();
        contextData.put("key1", "value1");
        contextData.put("key2", "value2");
        HashMap<String, Object> detailMap = new HashMap<>();
        detailMap.put("contextdata", contextData);
        HashMap<String, Object> consequence = new HashMap<>();
        consequence.put(AnalyticsConstants.EventDataKeys.ID, "id");
        consequence.put(AnalyticsConstants.EventDataKeys.TYPE, "an");
        consequence.put(AnalyticsConstants.EventDataKeys.DETAIL, detailMap);
        HashMap<String, Object> triggeredConsequence = new HashMap<>();
        triggeredConsequence.put(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        EventData eventData = new EventData();
        eventData.putObject(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE, consequence);
        Event sampleEvent = new Event.Builder("rule event", EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT).setData(eventData).build();

        // test
        rulesEngineResponseContentListener.hear(sampleEvent);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsExtension, times(0)).handleRulesEngineEvent(sampleEvent);
    }
}
