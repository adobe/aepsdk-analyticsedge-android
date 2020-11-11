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

import static com.adobe.marketing.mobile.AnalyticsEdgeConstants.EXTENSION_NAME;
import static com.adobe.marketing.mobile.AnalyticsEdgeConstants.EXTENSION_VERSION;
import static com.adobe.marketing.mobile.AnalyticsEdgeConstants.LOG_TAG;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsEdgeInternal extends Extension implements EventsHandler {

    private ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
    private static final String MODULE_NAME = "com.adobe.aepsdk.module.analyticsedge";
    private PlatformServices platformServices = new AndroidPlatformServices();
    private ExecutorService executorService;
    private final Object executorMutex = new Object();
    private AnalyticsEdgeState analyticsEdgeState;

    /**
     * Constructor.
     *
     * <p>
     * Called during the Analytics Edge extension's registration.
     * The following listeners are registered during this extension's registration.
     * <ul>
     *     <li> {@link ConfigurationResponseContentListener} listening to event with eventType {@link EventType#CONFIGURATION}
     *     <li> {@link GenericTrackRequestContentListener} listening to event with eventType {@link EventType#GENERIC_TRACK}
     *     and EventSource {@link EventSource#RESPONSE_CONTENT}</li>
     * </ul>
     *
     * @param extensionApi 	{@link ExtensionApi} instance
     */
    protected AnalyticsEdgeInternal(final ExtensionApi extensionApi) {
        super(extensionApi);
        registerEventListeners(extensionApi);

        // Init the analytics edge state
        analyticsEdgeState = new AnalyticsEdgeState();
    }

    /**
     * Overridden method of {@link Extension} class to provide a valid extension name to register with eventHub.
     *
     * @return A {@link String} extension name for Messaging
     */
    @Override
    protected String getName() {
        return EXTENSION_NAME;
    }

    /**
     * Overridden method of {@link Extension} class to provide the extension version.
     *
     * @return A {@link String} representing the extension version
     */
    @Override
    protected String getVersion() {
        return EXTENSION_VERSION;
    }

    /**
     * Overridden method of {@link Extension} class called when extension is unregistered by the core.
     *
     * <p>
     * On unregister of messaging extension, the shared states are cleared.
     */
    @Override
    protected void onUnregistered() {
        super.onUnregistered();
        getApi().clearSharedEventStates(null);
    }

    /**
     * Overridden method of {@link Extension} class to handle error occurred during registration of the module.
     *
     * @param extensionUnexpectedError 	{@link ExtensionUnexpectedError} occurred exception
     */
    @Override
    protected void onUnexpectedError(ExtensionUnexpectedError extensionUnexpectedError) {
        super.onUnexpectedError(extensionUnexpectedError);
        this.onUnregistered();
    }

    private void registerEventListeners(final ExtensionApi extensionApi) {
        extensionApi.registerListener(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, ConfigurationResponseContentListener.class);
        extensionApi.registerListener(EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT, GenericTrackRequestContentListener.class);

        Log.debug(AnalyticsEdgeConstants.LOG_TAG, "Registering Analytics Edge extension - version %s",
                AnalyticsEdgeConstants.EXTENSION_VERSION);
    }

    /**
     * This method queues the provided event in {@link #eventQueue}.
     *
     * <p>
     * The queued events are then processed in an orderly fashion.
     * No action is taken if the provided event's value is null.
     *
     * @param event 	The {@link Event} thats needs to be queued
     */
    void queueEvent(final Event event) {
        if (event == null) {
            return;
        }

        eventQueue.add(event);
    }

    /**
     * Processes the queued event one by one until queue is empty.
     *
     * <p>
     * Suspends processing of the events in the queue if the configuration shared state is not ready.
     * Processed events are polled out of the {@link #eventQueue}.
     */
    void processEvents() {
        while (!eventQueue.isEmpty()) {
            Event eventToProcess = eventQueue.peek();

            if (eventToProcess == null) {
                Log.debug(AnalyticsEdgeConstants.LOG_TAG, "Unable to process event, Event received is null.");
                return;
            }

            ExtensionErrorCallback<ExtensionError> configurationErrorCallback = new ExtensionErrorCallback<ExtensionError>() {
                @Override
                public void error(final ExtensionError extensionError) {
                    if (extensionError != null) {
                        Log.warning(AnalyticsEdgeConstants.LOG_TAG,
                                String.format("AnalyticsEdgeInternal : Could not process event, an error occurred while retrieving configuration shared state: %s",
                                        extensionError.getErrorName()));
                    }
                }
            };

            final Map<String, Object> configSharedState = getApi().getSharedEventState(AnalyticsEdgeConstants.EventDataKeys.Configuration.EXTENSION_NAME,
                    eventToProcess, configurationErrorCallback);

            // NOTE: configuration is mandatory processing the event, so if shared state is null (pending) stop processing events
            if (configSharedState == null) {
                Log.warning(AnalyticsEdgeConstants.LOG_TAG,
                        "AnalyticsEdgeInternal : Could not process event, configuration shared state is pending");
                return;
            }

            else if (EventType.GENERIC_TRACK.getName().equalsIgnoreCase(eventToProcess.getType()) &&
                    EventSource.REQUEST_CONTENT.getName().equalsIgnoreCase(eventToProcess.getSource())) {
                // handle the track event information from the generic track request content event
                handleGenericTrackEvent(eventToProcess);
            }

            // event processed, remove it from the queue
            eventQueue.poll();
        }
    }

    @Override
    public void processConfigurationResponse(final Event event) {
        if (event == null) {
            Log.debug(AnalyticsEdgeConstants.LOG_TAG, "Unable to handle configuration response. Event received is null.");
            return;
        }

        final EventData configData = event.getData();

        // save to analytics state
        analyticsEdgeState.extractConfigurationInfo(configData);

        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                if (!MobilePrivacyStatus.OPT_IN.equals(analyticsEdgeState.getPrivacyStatus())) {
                    optOut();
                    return;
                }

                processEvents();
            }
        });
    }

    @Override
    public void handleGenericTrackEvent(final Event event) {
        if (event == null) {
            Log.debug(LOG_TAG, "Unable to send track request. Event data received is null");
            return;
        }

        if (event.getEventType() == EventType.GENERIC_TRACK) {
           // TODO: handle track event
        }
    }

    private void optOut() {
        eventQueue.clear();
    }

    private static Map<String, Object> jsonStringToMap(final String jsonString) throws JSONException {
        final HashMap<String, Object> map = new HashMap<String, Object>();
        final JSONObject jObject = new JSONObject(jsonString);
        final Iterator<String> keys = jObject.keys();

        while( keys.hasNext() ) {
            final String key = keys.next();
            final Object value = jObject.get(key);
            map.put(key, value);
        }

        return map;
    }

    // ========================================================================================
    // Getters for private members
    // ========================================================================================

    /**
     * Getter for the {@link #executorService}. Access to which is mutex protected.
     *
     * @return A non-null {@link ExecutorService} instance
     */
    ExecutorService getExecutor() {
        synchronized (executorMutex) {
            if (executorService == null) {
                executorService = Executors.newSingleThreadExecutor();
            }

            return executorService;
        }
    }

    /**
     * Getter for the {@link #eventQueue}.
     *
     * @return A non-null {@link ConcurrentLinkedQueue} instance
     */
    ConcurrentLinkedQueue<Event> getEventQueue() {
        return eventQueue;
    }
}
