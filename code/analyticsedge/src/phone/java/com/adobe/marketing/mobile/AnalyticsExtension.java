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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.adobe.marketing.mobile.AnalyticsConstants.EXTENSION_NAME;
import static com.adobe.marketing.mobile.AnalyticsConstants.EXTENSION_VERSION;
import static com.adobe.marketing.mobile.AnalyticsConstants.LOG_TAG;

class AnalyticsExtension extends Extension implements EventsHandler {

    private ConcurrentLinkedQueue<Event> eventQueue = new ConcurrentLinkedQueue<>();
    private final PlatformServices platformServices;
    private ExecutorService executorService;
    private final Object executorMutex = new Object();
    private Map<String, Object> currentConfiguration = new HashMap<>(); // the last valid config shared state
    private AnalyticsHelper analyticsHelper;
    private String applicationIdentifier;
    private String analyticsId;
    private String visitorId;

    /**
     * Constructor.
     *
     * <p>
     * Called during the Analytics Edge extension's registration.
     * The following listeners are registered during this extension's registration.
     * <ul>
     *     <li> {@link ConfigurationResponseContentListener} listening to event with eventType {@link EventType#CONFIGURATION}
     *     and EventSource {@link EventSource#RESPONSE_CONTENT}</li>
     *     <li> {@link GenericTrackRequestContentListener} listening to event with eventType {@link EventType#GENERIC_TRACK}
     *     and EventSource {@link EventSource#REQUEST_CONTENT}</li>
     *     <li> {@link RulesEngineResponseContentListener} listening to event with eventType {@link EventType#RULES_ENGINE}
     *     and EventSource {@link EventSource#RESPONSE_CONTENT}</li>
     * </ul>
     *
     * @param extensionApi  {@link ExtensionApi} instance
     */
    protected AnalyticsExtension(final ExtensionApi extensionApi) {
        super(extensionApi);
        registerEventListeners(extensionApi);
        this.platformServices = new AndroidPlatformServices();
        this.analyticsHelper = new AnalyticsHelper(platformServices);
        this.applicationIdentifier = analyticsHelper.getApplicationIdentifier();
        initializeAIDAndVID();
    }

    /**
     * Constructor for testing purposes.
     *
     * @param extensionApi  {@link ExtensionApi} instance
     * @param platformServices the mocked {@link PlatformServices} instance
     */
    AnalyticsExtension(ExtensionApi extensionApi, PlatformServices platformServices) {
        super(extensionApi);
        registerEventListeners(extensionApi);
        this.platformServices = platformServices;
        this.analyticsHelper = new AnalyticsHelper(platformServices);
        this.applicationIdentifier = analyticsHelper.getApplicationIdentifier();
        initializeAIDAndVID();
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
     * @param extensionUnexpectedError  {@link ExtensionUnexpectedError} occurred exception
     */
    @Override
    protected void onUnexpectedError(final ExtensionUnexpectedError extensionUnexpectedError) {
        super.onUnexpectedError(extensionUnexpectedError);
        this.onUnregistered();
    }

    /**
     * This method registers listeners for the Analytics extension.
     *
     * @param extensionApi  {@link ExtensionApi} instance
     */
    private void registerEventListeners(final ExtensionApi extensionApi) {
        extensionApi.registerListener(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, ConfigurationResponseContentListener.class);
        extensionApi.registerListener(EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT, GenericTrackRequestContentListener.class);
        extensionApi.registerListener(EventType.RULES_ENGINE, EventSource.RESPONSE_CONTENT, RulesEngineResponseContentListener.class);

        Log.debug(AnalyticsConstants.LOG_TAG, "Registering Analytics extension - version %s",
                AnalyticsConstants.EXTENSION_VERSION);
    }

    /**
     * Processes the queued event one by one until queue is empty.
     *
     * <p>
     * Suspends processing of the events in the queue if the configuration shared state is not ready.
     * Processed events are polled out of the {@link #eventQueue}.
     */
    private void processEvents() {
        while (!eventQueue.isEmpty()) {
            final Event eventToProcess = eventQueue.peek();

            if (eventToProcess == null) {
                Log.debug(AnalyticsConstants.LOG_TAG, "Unable to process event, Event received is null.");
                return;
            }

            currentConfiguration = getApi().getSharedEventState(AnalyticsConstants.SharedStateKeys.CONFIGURATION,
                    eventToProcess, null);

            if (MobilePrivacyStatus.OPT_OUT.equals(getPrivacyStatus())) {
                optOut();
                return;
            }

            final Map<String, Object> eventData = eventToProcess.getEventData();

            // NOTE: configuration is mandatory to process an event, so if shared state is null (pending) stop processing events
            if (currentConfiguration == null) {
                Log.warning(AnalyticsConstants.LOG_TAG,
                        "AnalyticsInternal : Could not process event, configuration shared state is pending");
                return;
            }

            else if (EventType.GENERIC_TRACK.getName().equalsIgnoreCase(eventToProcess.getType()) &&
                    EventSource.REQUEST_CONTENT.getName().equalsIgnoreCase(eventToProcess.getSource())) {
                // handle the track event information from the generic track request content event
                track(eventToProcess, eventData);
            }

            else if (EventType.RULES_ENGINE.getName().equalsIgnoreCase(eventToProcess.getType())) {
                processRulesEngineResponseEvent(eventToProcess);
            }

            // event processed, remove it from the queue
            eventQueue.poll();
        }
    }

    /**
     * Adds the Rules Configuration Response Content event to the event queue to be processed.
     *
     * <p>
     * Any events in the event queue will be cleared if the privacy status is opted out.
     *
     * @param event The Configuration Response Content {@link Event} to be processed.
     */
    @Override
    public void handleConfigurationEvent(final Event event) {
        if (event == null) {
            Log.debug(AnalyticsConstants.LOG_TAG, "Unable to handle configuration response. Event received is null.");
            return;
        }

        eventQueue.add(event);
        processEvents();
    }

    /**
     * Adds the Generic Track Request Content event to the event queue to be processed.
     *
     * @param event The Generic Track Request Content {@link Event} to be processed.
     */
    @Override
    public void handleAnalyticsTrackEvent(final Event event) {
        if (event == null) {
            Log.trace(LOG_TAG, "handleAnalyticsTrackEvent - Event with id %s contained no data, ignoring.");
            return;
        }

        if (MobilePrivacyStatus.OPT_OUT.equals(getPrivacyStatus())) {
            Log.debug(LOG_TAG, "handleAnalyticsTrackEvent - Dropping track request, privacy is opted-out.");
            return;
        }

        if (event.getEventType() == EventType.GENERIC_TRACK) {
            Log.trace(LOG_TAG, "handleAnalyticsTrackEvent - Processing event with id %s.", event.getUniqueIdentifier());
            eventQueue.add(event);
            processEvents();
        }
    }

    /**
     * Adds the Rules Engine Response Content event to the event queue to be processed.
     *
     * <p>
     * Any events in the event queue will be cleared if the privacy status is opted out.
     *
     * @param event The Rules Engine Response Content {@link Event} to be processed.
     */
    @Override
    public void handleRulesEngineEvent(final Event event) {
        if (event == null) {
            Log.debug(AnalyticsConstants.LOG_TAG, "Unable to handle rules engine response. Event received is null.");
            return;
        }

        eventQueue.add(event);
        processEvents();
    }

    /**
     * Processes the passed in Rules Engine Response Content event.
     *
     * @param event The Rules Engine Response Content {@link Event} to be processed.
     */
    private void processRulesEngineResponseEvent(final Event event) {
        Map<String, Object> data = event.getEventData();
        if (data == null || data.isEmpty()) {
            Log.debug(LOG_TAG, "processRulesEngineResponseEvent - Event with id %s contained no data, ignoring.", event.getUniqueIdentifier());
            return;
        }

        Log.trace(LOG_TAG, "processRulesEngineResponseEvent - Processing event with id %s.", event.getUniqueIdentifier());

        final Map<String,Object> consequence = (Map<String,Object>) data.get(AnalyticsConstants.EventDataKeys.TRIGGERED_CONSEQUENCE);
        if (consequence == null || consequence.isEmpty()) {
            Log.trace(LOG_TAG, "processRulesEngineResponseEvent - Ignoring as missing consequence data in event with id %s.", event.getUniqueIdentifier());
            return;
        }

        final String consequenceType = (String) consequence.get(AnalyticsConstants.EventDataKeys.TYPE);
        if (!AnalyticsConstants.ConsequenceTypes.TRACK.equals(consequenceType)) {
            Log.trace(LOG_TAG, "processRulesEngineResponseEvent - Ignoring as consequence type is not analytics in event with id %s.", event.getUniqueIdentifier());
            return;
        }

        final String consequenceId = (String) consequence.get(AnalyticsConstants.EventDataKeys.ID);
        if (StringUtils.isNullOrEmpty(consequenceId)) {
            Log.trace(LOG_TAG, "processRulesEngineResponseEvent - Ignoring as consequence id is missing in event with id %s.", event.getUniqueIdentifier());
            return;
        }

        Map<String, Object> consequenceDetail = new HashMap<>();
        if (consequence.get(AnalyticsConstants.EventDataKeys.DETAIL) != null) {
            consequenceDetail = (Map<String, Object>) consequence.get(AnalyticsConstants.EventDataKeys.DETAIL);
        }

        track(event, consequenceDetail);
    }

    /**
     * This method clears the event queue when privacy status is opted out.
     */
    private void optOut() {
        Log.debug(LOG_TAG, "Privacy status is opted out, clearing event queue.");
        eventQueue.clear();

        //Set analyticsId and visitorId null on optout
        analyticsId = null;
        visitorId = null;
        //remove AID and VID from data store
        if (platformServices == null) {
            Log.debug(LOG_TAG, "optout - can't remove AID and VID from data store. Platformservices is null.");
            return;
        }
        final LocalStorageService.DataStore dataStore = platformServices.getLocalStorageService().getDataStore(AnalyticsConstants.DATASTORE_NAME);
        if (dataStore == null) {
            Log.debug(LOG_TAG, "optout - Failed to remove AID and VID from datastore on optout. DataStore is null.");
            return;
        }
        dataStore.remove(AnalyticsConstants.DataStoreKeys.ANALYTICS_ID);
        dataStore.remove(AnalyticsConstants.DataStoreKeys.VISITOR_ID);
        Log.debug(LOG_TAG, "optout - Removed AID and VID from datastore on optout.");
    }

    /**
     * Returns the privacy status present in the last valid configuration.
     *
     * @return The {@link MobilePrivacyStatus} present in the configuration.
     */
    private MobilePrivacyStatus getPrivacyStatus() {
        if (currentConfiguration != null && !currentConfiguration.isEmpty()) {
            final Object currentPrivacy = currentConfiguration.get(AnalyticsConstants.Configuration.GLOBAL_CONFIG_PRIVACY);
            if (currentPrivacy != null) {
                return MobilePrivacyStatus.fromString(currentPrivacy.toString());
            }
        }
        return AnalyticsConstants.DEFAULT_PRIVACY_STATUS;
    }

    /**
     * Helper method to get the correct action prefix.
     *
     * @param isInternalAction A boolean signaling if the track request is internal.
     *
     * @return The action prefix {@link String} corresponding to the type of track request.
     */
    private String getActionPrefix(final boolean isInternalAction) {
        return isInternalAction ? AnalyticsConstants.INTERNAL_ACTION_PREFIX : AnalyticsConstants.ACTION_PREFIX;
    }

    /**
     * Helper method to get the correct action key.
     *
     * @param isInternalAction A boolean signaling if the track request is internal.
     *
     * @return The action key {@link String} corresponding to the type of track request.
     */
    private String getActionKey(final boolean isInternalAction) {
        return isInternalAction ? AnalyticsConstants.ContextDataKeys.INTERNAL_ACTION :
                AnalyticsConstants.ContextDataKeys.ACTION;
    }

    /**
     * This prepares the analytics variables and analytics data from the passed in event.
     *
     * @param event An event containing track data for processing
     * @param data Track data for processing
     */
    private void track(final Event event, final Map<String, Object> data) {
        if (data == null || !(data.containsKey(AnalyticsConstants.EventDataKeys.TRACK_STATE) ||
                data.containsKey(AnalyticsConstants.EventDataKeys.TRACK_ACTION) ||
                data.containsKey(AnalyticsConstants.EventDataKeys.CONTEXT_DATA))) {
            Log.warning(LOG_TAG, "track - Dropping request as event data is null or missing state, action or contextData");
            return;
        }
        final Map<String, String> analyticsVars = processAnalyticsVars(event, data);
        final Map<String, String> analyticsData = processAnalyticsData(event, data);
        sendAnalyticsHit(analyticsVars, analyticsData);
    }

    /**
     * This method converts the event's event data into analytics variables.
     *
     * @param event The Generic Track Request Content {@link Event}.
     * @param data Track data for processing
     *
     * @return {@code Map<String, String>} containing the vars data
     */
    private HashMap<String, String> processAnalyticsVars(final Event event, final Map<String, Object> data) {
        final HashMap<String, String> processedVars = new HashMap<>();
        // Context: pe/pev2 values should always be present in track calls if there's action regardless of state.
        // If state is present then pageName = state name else pageName = app id to prevent hit from being discarded.
        final String actionName = (String) data.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION);
        if (!StringUtils.isNullOrEmpty(actionName)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.IGNORE_PAGE_NAME, AnalyticsConstants.IGNORE_PAGE_NAME_VALUE);
            boolean isInternal = false;
            if (data.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL) != null) {
                isInternal = (boolean) data.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL);
            }
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.ACTION_NAME, getActionPrefix(isInternal) + actionName);
        }

        final String stateName = (String) data.get(AnalyticsConstants.EventDataKeys.TRACK_STATE);
        if (!StringUtils.isNullOrEmpty(stateName)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME, stateName);
        } else {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.PAGE_NAME, applicationIdentifier);
        }

        if (!StringUtils.isNullOrEmpty(analyticsId)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.ANALYTICS_ID, analyticsId);
        }
        if (!StringUtils.isNullOrEmpty(visitorId)) {
            processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.VISITOR_ID, visitorId);
        }

        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.CHARSET, AnalyticsConstants.CHARSET);
        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.FORMATTED_TIMESTAMP, AnalyticsConstants.TIMESTAMP_TIMEZONE_OFFSET);

        // Set timestamp for all requests.
        processedVars.put(AnalyticsConstants.AnalyticsRequestKeys.STRING_TIMESTAMP, Long.toString(event.getTimestampInSeconds()));

        final UIService uiService = platformServices.getUIService();

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

    /**
     * This method converts the event's event data into analytics variables.
     *
     * @param event The Generic Track Request Content {@link Event}.
     * @param data Track data for processing
     *
     * @return {@code Map<String, String>} containing the context data
     */
    private HashMap<String, String> processAnalyticsData(final Event event, final Map<String, Object> data) {
        final HashMap<String, String> processedContextData = new HashMap<>();

        final Map<String, String> contextData = (Map<String, String>) data.get(AnalyticsConstants.EventDataKeys.CONTEXT_DATA);
        if (contextData != null && !contextData.isEmpty()) {
            Iterator iterator = contextData.entrySet().iterator();
            while(iterator.hasNext()){
                Map.Entry<String, String> currentEntry = (Map.Entry<String, String>) iterator.next();
                processedContextData.put(currentEntry.getKey(), currentEntry.getValue());
            }
        }

        final String actionName = (String) data.get(AnalyticsConstants.EventDataKeys.TRACK_ACTION);
        if (!StringUtils.isNullOrEmpty(actionName)) {
            boolean isInternal = false;
            if (data.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL) != null) {
                isInternal = (boolean) data.get(AnalyticsConstants.EventDataKeys.TRACK_INTERNAL);
            }
            processedContextData.put(getActionKey(isInternal), actionName);
        }

        if (getPrivacyStatus() == MobilePrivacyStatus.UNKNOWN) {
            processedContextData.put(AnalyticsConstants.AnalyticsRequestKeys.PRIVACY_MODE, "unknown");
        }

        if (isAssuranceSessionActive(event)) {
            processedContextData.put(AnalyticsConstants.ContextDataKeys.EVENT_IDENTIFIER, event.getUniqueIdentifier());
        }

        return processedContextData;
    }

    /**
     * Returns true if the Assurance session is active false otherwise. Determines it by checking if the Assurance
     * shared state contains a non null and non empty session id.
     * @param event The {@link EventData} to be use for getting Assurance Shared state.
     * @return a boolean, true if the Assurance session is active false otherwise.
     */
    private boolean isAssuranceSessionActive(final Event event) {
        if (event == null || event.getEventData() == null) {
            Log.debug(LOG_TAG, "isAssuranceSessionActive - event or event data is null. Returning false.");
            return false;
        }

        final ExtensionApi extensionApi = getApi();
        final EventData assuranceSharedState = extensionApi.getSharedEventState(AnalyticsConstants.SharedStateKeys.ASSURANCE, event);
        if (assuranceSharedState == null) {
            return false;
        }
        final String sessionId = assuranceSharedState.optString(AnalyticsConstants.EventDataKeys.SESSION_ID,"");
        return !StringUtils.isNullOrEmpty(sessionId);
    }

    /**
     * This method sends the analytics data to the Edge extension to be sent to the Edge.
     *
     * @param analyticsVars {@code Map<String, String>} containing the analytics vars
     * @param analyticsData {@code Map<String, String>} containing the analytics context data
     *
     */
    private void sendAnalyticsHit(final Map<String, String> analyticsVars, final Map<String, String> analyticsData) {
        final HashMap<String, Object> legacyAnalyticsData = new HashMap<>();
        final HashMap<String, String> contextData = new HashMap<>();

        legacyAnalyticsData.putAll(analyticsVars);

        legacyAnalyticsData.put("ndh", 1);

        // It takes the provided data map and removes key-value pairs where the key is null or is prefixed with "&&"
        // The prefixed ones will be moved in the vars map
        if (!analyticsData.isEmpty()) {
            for (Map.Entry<String, String> entry : analyticsData.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(AnalyticsConstants.VAR_ESCAPE_PREFIX)){
                    final String strippedKey = key.substring(AnalyticsConstants.VAR_ESCAPE_PREFIX.length());
                    legacyAnalyticsData.put(strippedKey, entry.getValue());
                } else if (!StringUtils.isNullOrEmpty(key)){
                    contextData.put(key, entry.getValue());
                }
            }
        }

        legacyAnalyticsData.put(AnalyticsConstants.XDMDataKeys.CONTEXT_DATA, contextData);

        // create experienceEvent and send the hit using the edge extension
        final HashMap<String, String> xdm = new HashMap<>();
        xdm.put(AnalyticsConstants.XDMDataKeys.EVENTTYPE, AnalyticsConstants.ANALYTICS_XDM_EVENTTYPE);
        final HashMap<String, Object> edgeEventData = new HashMap<>();
        final HashMap<String, Object> edgeLegacyData = new HashMap<String, Object>() {
            {
                put(AnalyticsConstants.XDMDataKeys.ANALYTICS, legacyAnalyticsData);
            }
        };
        edgeEventData.put(AnalyticsConstants.XDMDataKeys.LEGACY, edgeLegacyData);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put(AnalyticsConstants.XDMDataKeys.XDM, xdm);
        eventData.put(AnalyticsConstants.XDMDataKeys.DATA, edgeEventData);
        final Event event = new Event.Builder(
                AnalyticsConstants.ANALYTICS_XDM_EVENTNAME,
                EventType.get(AnalyticsConstants.Edge.EVENT_TYPE),
                EventSource.REQUEST_CONTENT).setEventData(eventData).build();

        MobileCore.dispatchEvent(event, null);
    }

    /**
     * Initializes the variables {@link #analyticsId} and {@link #visitorId} using values stored in local storage.
     */
    private void initializeAIDAndVID() {
        if (platformServices == null) {
            Log.debug(LOG_TAG, "addAIDAndVIDToAnalyticsVars - Unable to initialize AID and VID. PlatformServices is null.");
            return;
        }
        final LocalStorageService.DataStore dataStore = platformServices.getLocalStorageService().getDataStore(AnalyticsConstants.DATASTORE_NAME);
        if (dataStore == null) {
            Log.debug(LOG_TAG, "addAIDAndVIDToAnalyticsVars - Unable to initialize AID and VID. DataStore is null.");
            return;
        }
        analyticsId = dataStore.getString(AnalyticsConstants.DataStoreKeys.ANALYTICS_ID, null);
        visitorId = dataStore.getString(AnalyticsConstants.DataStoreKeys.VISITOR_ID, null);
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
}
