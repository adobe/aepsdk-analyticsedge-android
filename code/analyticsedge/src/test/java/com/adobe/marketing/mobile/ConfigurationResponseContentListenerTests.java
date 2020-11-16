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
@PrepareForTest({ExtensionApi.class, App.class, Context.class, AnalyticsInternal.class})

public class ConfigurationResponseContentListenerTests {

    private ConfigurationResponseContentListener configurationResponseContentListener;
    private int EXECUTOR_TIMEOUT = 5;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // Mocks
    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    Context context;
    @Mock
    AnalyticsInternal mockAnalyticsInternal;

    @Before
    public void setup() {
        PowerMockito.mockStatic(App.class);
        Mockito.when(App.getAppContext()).thenReturn(context);
    }

    @Before
    public void beforeEach() {
        configurationResponseContentListener = new ConfigurationResponseContentListener(mockExtensionApi, EventType.CONFIGURATION.getName(), EventSource.RESPONSE_CONTENT.getName());
        when(mockAnalyticsInternal.getExecutor()).thenReturn(executor);
        when(mockExtensionApi.getExtension()).thenReturn(mockAnalyticsInternal);
    }

    @Test
    public void test_validConfigurationResponseContentEvent() {
        // setup
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");
            }
        };
        Event sampleEvent = new Event.Builder("config event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT).setEventData(configData).build();

        // test
        configurationResponseContentListener.hear(sampleEvent);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsInternal, times(1)).processConfigurationResponse(sampleEvent);
    }

    @Test
    public void test_nullConfigurationResponseContentEvent() {
        // test
        configurationResponseContentListener.hear(null);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsInternal, times(0)).processConfigurationResponse(null);
    }

    @Test
    public void test_hearConfigurationResponseContentEventWhenParentExtensionIsNull() {
        // setup
        when(mockExtensionApi.getExtension()).thenReturn(null);
        HashMap<String,Object> configData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");
            }
        };
        Event sampleEvent = new Event.Builder("config event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT).setEventData(configData).build();

        // test
        configurationResponseContentListener.hear(sampleEvent);

        // verify
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);
        verify(mockAnalyticsInternal, times(0)).processConfigurationResponse(sampleEvent);
    }
}
