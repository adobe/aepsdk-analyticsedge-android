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

import static com.adobe.marketing.mobile.AnalyticsConstants.EXTENSION_NAME;
import static com.adobe.marketing.mobile.AnalyticsConstants.EXTENSION_VERSION;
import static com.adobe.marketing.mobile.AnalyticsConstants.LOG_TAG;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsInternal extends Extension implements EventsHandler {

    private ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
    private PlatformServices platformServices = new AndroidPlatformServices();
    private ExecutorService executorService;
    private final Object executorMutex = new Object();

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
    protected AnalyticsInternal(final ExtensionApi extensionApi) {
        super(extensionApi);
        registerEventListeners(extensionApi);
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
        Log.trace(LOG_TAG, "Extension unregistered from MobileCore: %s", AnalyticsConstants.FRIENDLY_NAME);
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

        Log.debug(AnalyticsConstants.LOG_TAG, "Registering Analytics extension - version %s",
                AnalyticsConstants.EXTENSION_VERSION);
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
                Log.debug(AnalyticsConstants.LOG_TAG, "Unable to process event, Event received is null.");
                return;
            }

            ExtensionErrorCallback<ExtensionError> configurationErrorCallback = new ExtensionErrorCallback<ExtensionError>() {
                @Override
                public void error(final ExtensionError extensionError) {
                    if (extensionError != null) {
                        Log.warning(AnalyticsConstants.LOG_TAG,
                                String.format("AnalyticsInternal : Could not process event, an error occurred while retrieving configuration shared state: %s",
                                        extensionError.getErrorName()));
                    }
                }
            };

            final Map<String, Object> configSharedState = getApi().getSharedEventState(AnalyticsConstants.SharedStateKeys.CONFIGURATION,
                    eventToProcess, configurationErrorCallback);

            // NOTE: configuration is mandatory processing the event, so if shared state is null (pending) stop processing events
            if (configSharedState == null) {
                Log.warning(AnalyticsConstants.LOG_TAG,
                        "AnalyticsInternal : Could not process event, configuration shared state is pending");
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
            Log.debug(AnalyticsConstants.LOG_TAG, "Unable to handle configuration response. Event received is null.");
            return;
        }

        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                if (MobilePrivacyStatus.OPT_OUT.equals(getPrivacyStatus(event))) {
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
            Log.trace(LOG_TAG, "handleGenericTrackEvent - Event with id %s contained no data, ignoring.");
            return;
        }

        if (event.getEventType() == EventType.GENERIC_TRACK) {
            Log.trace(LOG_TAG, "handleGenericTrackEvent - Processing event with id %s.", event.getUniqueIdentifier());
            track(event);
        }
    }

    private void optOut() {
        eventQueue.clear();
    }

    /**
     * Returns the privacy status present in the last valid configuration shared state.
     *
     * @return The {@link MobilePrivacyStatus} present in the configuration shared state.
     */
    private MobilePrivacyStatus getPrivacyStatus(Event event) {
        EventData configSharedState = getApi().getSharedEventState(AnalyticsConstants.SharedStateKeys.CONFIGURATION, event);
        if(configSharedState != null) {
            return MobilePrivacyStatus.fromString(configSharedState.optString(
                    AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY,
                    AnalyticsConstants.DEFAULT_PRIVACY_STATUS.getValue()));
        }
        return AnalyticsConstants.DEFAULT_PRIVACY_STATUS;
    }

    private String getActionPrefix(boolean isInternalAction) {
        return isInternalAction ? AnalyticsConstants.INTERNAL_ACTION_PREFIX : AnalyticsConstants.ACTION_PREFIX;
    }

    private String getActionKey(boolean isInternalAction) {
        return isInternalAction ? AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION_KEY :
                AnalyticsConstants.ContextDataKeys.ACTION_KEY;
    }

    private void track(Event event) {
        if(getPrivacyStatus(event).equals(MobilePrivacyStatus.OPT_OUT)) {
            Log.warning(LOG_TAG, "track - Dropping track request (Privacy is opted out).");
            return;
        }
        HashMap<String, String> analyticsVars = processAnalyticsVars(event);
        HashMap<String, String> analyticsData = processAnalyticsData(event);
        sendAnalyticsHit(analyticsVars, analyticsData);
    }

    private HashMap<String, String> processAnalyticsVars(Event event) {
        HashMap<String, String> processedVars = new HashMap<>();
        EventData eventData = event.getData();

        if(eventData == null || eventData.isEmpty()) {
            return processedVars;
        }

        // context: pe/pev2 values should always be present in track calls if there's action regardless of state.
        // If state is present then pageName = state name else pageName = app id to prevent hit from being discarded.
        String actionName = eventData.optString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, null);
        if(!StringUtils.isNullOrEmpty(actionName)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME, AnalyticsConstants.IGNORE_PAGE_NAME_VALUE);
            boolean isInternal = eventData.optBoolean(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, false);
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME, getActionPrefix(isInternal) + actionName);
        }
        // Todo :- We currently read application id from lifecycle
        // processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME, state->GetApplicationId());
        String stateName = eventData.optString(AnalyticsConstants.EventDataKeys.TRACK_STATE, null);
        if(!StringUtils.isNullOrEmpty(stateName)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME, stateName);
        }

        // Todo:- Aid. Should we add it to identity map or vars
        // Todo:- Vid. Should we add it to identity map or vars
        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.CHARSET, AnalyticsConstants.CHARSET);
        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP, AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET);

        // Set timestamp for all requests.
        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP, Long.toString(event.getTimestampInSeconds()));

        // Todo:- GetAnalyticsIdVisitorParameters ??
        UIService uiService = platformServices.getUIService();

        if (uiService != null) {
            if (uiService.getAppState() == UIService.AppState.BACKGROUND) {
                processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE,
                        AnalyticsConstants.APP_STATE_BACKGROUND);
            } else {
                processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.CUSTOMER_PERSPECTIVE,
                        AnalyticsConstants.APP_STATE_FOREGROUND);
            }
        }

        return processedVars;
    }

    private HashMap<String, String> processAnalyticsData(Event event) {
        HashMap<String, String> processedData = new HashMap<>();
        EventData eventData = event.getData();

        if(eventData == null || eventData.isEmpty()) {
            return processedData;
        }

        // Todo:- Should we append default lifecycle context data (os version, device name, device version, etc) to each hits?
        Map<String, Object> contextData = event.getEventData();
        if(!contextData.isEmpty()) {
            for (Map.Entry<String, Object> entry : contextData.entrySet()) {
                processedData.put(entry.getKey(), entry.getValue().toString());
            }
        }

        String actionName = eventData.optString(AnalyticsConstants.EventDataKeys.TRACK_ACTION, null);
        if(!StringUtils.isNullOrEmpty(actionName)) {
            boolean isInternal = eventData.optBoolean(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL, false);
            processedData.put(getActionKey(isInternal), actionName);
        }

        // Todo :- Is TimeSinceLaunch" param is required? If so, calculate by listening to lifecycle shared state update
        if(getPrivacyStatus(event) == MobilePrivacyStatus.UNKNOWN) {
            processedData.put(AnalyticsConstants.AnalyticsRequestKeys.PRIVACY_MODE, "unknown");
        }

        return processedData;
    }

    private void sendAnalyticsHit(HashMap<String, String> analyticsVars, HashMap<String, String> analyticsData) {
        HashMap<String, Object> legacyAnalyticsData = new HashMap<>();
        HashMap<String, String> contextData = new HashMap<>();

        legacyAnalyticsData.putAll(analyticsVars);

        legacyAnalyticsData.put("ndh", 1);

        // It takes the provided data map and removes key-value pairs where the key is null or is prefixed with "&&"
        // The prefixed ones will be moved in the vars map
        if(!analyticsData.isEmpty()) {
            for (Map.Entry<String, String> entry : contextData.entrySet()) {
                String key = entry.getKey();
                if(key.startsWith(AnalyticsConstants.VAR_ESCAPE_PREFIX)){
                    String strippedKey = key.substring(AnalyticsConstants.VAR_ESCAPE_PREFIX.length());
                    legacyAnalyticsData.put(strippedKey, entry.getValue());
                }
            }
        }

        legacyAnalyticsData.put(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA, contextData);

        // create experienceEvent and send the hit using the edge extension
        HashMap<String, Object> edgeEventData = new HashMap<>();
        HashMap<String, Object> xdm = new HashMap<>();
        xdm.put(AnalyticsConstants.XDMDataKeys.EVENTTYPE, AnalyticsConstants.ANALYTICS_XDM_EVENTTYPE);
        Map.Entry<String, Object> edgeLegacyData = new HashMap.SimpleEntry<String, Object>(AnalyticsConstants.XDMDataKeys.ANALYTICS, legacyAnalyticsData);
        edgeEventData.put(AnalyticsConstants.XDMDataKeys.LEGACY, edgeLegacyData);
        ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(xdm).build();
        Edge.sendEvent(experienceEvent, null);
    }

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
