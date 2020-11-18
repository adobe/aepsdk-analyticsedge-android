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

import static com.adobe.marketing.mobile.AnalyticsConstants.EXTENSION_VERSION;
import static com.adobe.marketing.mobile.AnalyticsConstants.LOG_TAG;


public final class Analytics {

    private Analytics() {}

    /**
     * Returns the current version of the Analytics extension.
     *
     * @return A {@link String} representing the Analytics extension version
     */
    public static String extensionVersion() {
        return EXTENSION_VERSION;
    }

    /**
     * Registers the Analytics extension with the {@code MobileCore}.
     * <p>
     * This will allow the extension to send and receive events to and from the SDK.
     */
    public static void registerExtension() {
        if(MobileCore.getCore() == null || MobileCore.getCore().eventHub == null) {
            Log.warning(LOG_TAG, "Unable to register Analytics SDK since MobileCore is not initialized properly. For more details refer to https://aep-sdks.gitbook.io/docs/using-mobile-extensions/mobile-core");
        }

        MobileCore.registerExtension(AnalyticsExtension.class, new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {
                Log.debug("There was an error registering the Analytics Extension: %s", extensionError.getErrorName());
            }
        });
    }

    /**
     * Clears all hits from the tracking queue and removes them from the database.
     *
     */
    public static void clearQueue() {
        Log.debug(AnalyticsConstants.LOG_TAG, "clearQueue - is not currently supported with Edge");
    }

    /**
     * Retrieves the total number of analytics hits currently in the tracking queue.
     *
     * @param callback {@code AdobeCallback} invoked with the queue size {@code long} value;
     * when an {@link AdobeCallbackWithError} is provided, an {@link AdobeError} can be returned in the
     * eventuality of an unexpected error or if the default timeout (5000ms) is met before the callback is returned with queue size.
     *
     */
    public static void getQueueSize(final AdobeCallback<Long> callback) {
        Log.debug(AnalyticsConstants.LOG_TAG, "getQueueSize - is not currently supported with Edge");
        if (callback == null) {
            return;
        }

        final AdobeCallbackWithError adobeCallbackWithError = callback instanceof AdobeCallbackWithError ?
                (AdobeCallbackWithError)callback : null;

        if (adobeCallbackWithError != null) {
            adobeCallbackWithError.fail(AdobeError.UNEXPECTED_ERROR);
        } else {
            callback.call(0L);
        }
    }

    /**
     * Retrieves the analytics tracking identifier generated for this app/device instance.
     *
     * @param callback {@code AdobeCallback} invoked with the analytics identifier {@code String} value;
     * when an {@link AdobeCallbackWithError} is provided, an {@link AdobeError} can be returned in the
     * eventuality of an unexpected error or if the default timeout (5000ms) is met before the callback is returned with analytics tracking identifier.
     *
     */
    public static void getTrackingIdentifier(final AdobeCallback<String> callback) {
        Log.debug(AnalyticsConstants.LOG_TAG, "getTrackingIdentifier - is not currently supported with Edge");
        if (callback == null) {
            return;
        }

        final AdobeCallbackWithError adobeCallbackWithError = callback instanceof AdobeCallbackWithError ?
                (AdobeCallbackWithError)callback : null;

        if (adobeCallbackWithError != null) {
            adobeCallbackWithError.fail(AdobeError.UNEXPECTED_ERROR);
        } else {
            callback.call("");
        }
    }

    /**
     * Retrieves the visitor identifier
     * @param callback {@code AdobeCallback} invoked with the visitor identifier {@code String} value;
     *  when an {@link AdobeCallbackWithError} is provided, an {@link AdobeError} can be returned in the
     *  eventuality of an unexpected error or if the default timeout (5000ms) is met before the callback is returned with visitor identifier.
     */
    public static void getVisitorIdentifier(final AdobeCallback<String> callback) {
        Log.debug(AnalyticsConstants.LOG_TAG, "getVisitorIdentifier - is not currently supported with Edge");
        if (callback == null) {
            return;
        }

        final AdobeCallbackWithError adobeCallbackWithError = callback instanceof AdobeCallbackWithError ?
                (AdobeCallbackWithError)callback : null;

        if (adobeCallbackWithError != null) {
            adobeCallbackWithError.fail(AdobeError.UNEXPECTED_ERROR);
        } else {
            callback.call("");
        }
    }

    /**
     * Forces analytics to send all queued hits regardless of current batch options.
     */
    public static void sendQueuedHits() {
        Log.debug(AnalyticsConstants.LOG_TAG, "sendQueuedHits - is not currently supported with Edge");
    }

    /**
     * Sets the visitor identifier
     * @param visitorID {@code String} new value for visitor identifier
     */
    public static void setVisitorIdentifier(final String visitorID) {
        Log.debug(AnalyticsConstants.LOG_TAG, "setVisitorIdentifier - is not currently supported with Edge");
    }
}
