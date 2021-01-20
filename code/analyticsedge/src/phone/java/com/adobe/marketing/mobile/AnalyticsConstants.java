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

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

final class AnalyticsConstants {
    static final String LOG_TAG = "AnalyticsEdge";
    static final String EXTENSION_NAME = "com.adobe.module.analyticsedge";
    static final String FRIENDLY_NAME = "AnalyticsEdge";
    static final String EXTENSION_VERSION = "1.0.0-beta-5";
    static final String DATASTORE_NAME = EXTENSION_NAME;

    static final String APP_STATE_FOREGROUND = "foreground";
    static final String APP_STATE_BACKGROUND = "background";

    static final String ACTION_PREFIX = "AMACTION:";
    static final String INTERNAL_ACTION_PREFIX = "ADBINTERNAL:";
    static final String VAR_ESCAPE_PREFIX = "&&";
    static final String IGNORE_PAGE_NAME_VALUE = "lnk_o";
    static final String CHARSET = "UTF-8";

    static final MobilePrivacyStatus DEFAULT_PRIVACY_STATUS = MobilePrivacyStatus.UNKNOWN;

    static final class SharedStateKeys {
        static final String CONFIGURATION = "com.adobe.module.configuration";
        static final String ASSURANCE = "com.adobe.assurance";
    }

    static final class Configuration {
        static final String GLOBAL_CONFIG_PRIVACY = "global.privacy";
    }

    static final class Edge {
        static final String EVENT_TYPE = "com.adobe.eventType.edge";
    }

    static final class AnalyticsRequestKeys {
        static final String VISITOR_ID = "vid";
        static final String CHARSET = "ce";
        static final String FORMATTED_TIMESTAMP = "t";
        static final String STRING_TIMESTAMP = "ts";
        static final String CONTEXT_DATA = "c";
        static final String PAGE_NAME = "pageName";
        static final String IGNORE_PAGE_NAME = "pe";
        static final String CUSTOMER_PERSPECTIVE = "cp";
        static final String ACTION_NAME = "pev2";
        static final String ANALYTICS_ID = "aid";
        static final String PRIVACY_MODE = "a.privacy.mode";

    }

    static final class EventDataKeys {
        static final String FORCE_KICK_HITS  = "forcekick";
        static final String CLEAR_HITS_QUEUE = "clearhitsqueue";
        static final String ANALYTICS_ID     = "aid";
        static final String GET_QUEUE_SIZE   = "getqueuesize";
        static final String QUEUE_SIZE       = "queuesize";
        static final String TRACK_INTERNAL   = "trackinternal";
        static final String TRACK_ACTION     = "action";
        static final String TRACK_STATE      = "state";
        static final String CONTEXT_DATA = "contextdata";
        static final String ANALYTICS_SERVER_RESPONSE = "analyticsserverresponse";
        static final String VISITOR_IDENTIFIER = "vid";
        static final String RULES_CONSEQUENCE_TYPE_TRACK = "an";
        static final String HEADERS_RESPONSE = "headers";
        static final String ETAG_HEADER = "ETag";
        static final String SERVER_HEADER = "Server";
        static final String CONTENT_TYPE_HEADER = "Content-Type";
        static final String REQUEST_EVENT_IDENTIFIER = "requestEventIdentifier";
        static final String HIT_HOST = "hitHost";
        static final String HIT_URL = "hitUrl";
        static final String SESSION_ID = "sessionid";
    }

    static final class ContextDataKeys {
        static final String ACTION_KEY = "a.action";
        static final String INTERNAL_ACTION_KEY = "a.internalaction";
        static final String EVENT_IDENTIFIER_KEY = "a.DebugEventIdentifier";
    }

    static final class XDMDataKeys {
        static final String LEGACY = "_legacy";
        static final String ANALYTICS = "analytics";
        static final String EVENTTYPE = "eventType";
        static final String CONTEXT_DATA = "c";
        static final String DATA = "data";
        static final String XDM = "xdm";
    }

    static final String ANALYTICS_XDM_EVENTTYPE = "legacy.analytics";
    static final String ANALYTICS_XDM_EVENTNAME = "Analytics Edge Request";

    /**
     * Retrieves a correctly-formatted timestamp string; this function returns an all 0 string except for the timezoneOffset
     * backend platform only processes timezone offset from this string and it is wasted cycles to provide the rest of the data.
     */
    static final String TIMESTAMP_TIMEZONE_OFFSET;

    static {
        Calendar cal = Calendar.getInstance();
        TIMESTAMP_TIMEZONE_OFFSET = "00/00/0000 00:00:00 0 " + TimeUnit.MILLISECONDS.toMinutes((long)(cal.get(
                Calendar.ZONE_OFFSET) * -1) - cal.get(Calendar.DST_OFFSET));
    }
}