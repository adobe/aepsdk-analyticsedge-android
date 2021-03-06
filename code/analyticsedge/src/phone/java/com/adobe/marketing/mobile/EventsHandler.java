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

interface EventsHandler {

    /**
     * Handles the ConfigurationResponse event.
     * @param event Configuration response event which contains the privacy status information.
     */
    void handleConfigurationEvent(final Event event);

    /**
     * Handles the GenericTrackRequest event.
     * @param event Generic track request event
     */
    void handleAnalyticsTrackEvent(final Event event);

    /**
     * Handles the RulesEngineResponse event.
     * @param event Rules engine response event which contains an Analytics rule action.
     */
    void handleRulesEngineEvent(final Event event);
}
