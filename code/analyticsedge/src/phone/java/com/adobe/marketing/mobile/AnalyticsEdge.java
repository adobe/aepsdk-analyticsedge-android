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

import static com.adobe.marketing.mobile.AnalyticsEdgeConstants.EXTENSION_VERSION;
import static com.adobe.marketing.mobile.AnalyticsEdgeConstants.LOG_TAG;


public final class AnalyticsEdge {

    private AnalyticsEdge() {}

    /**
     * Returns the current version of the Analytics Edge extension.
     *
     * @return A {@link String} representing the Analytics Edge extension version
     */
    public static String extensionVersion() {
        return EXTENSION_VERSION;
    }

    /**
     * Registers the Analytics Edge extension with the {@code MobileCore}.
     * <p>
     * This will allow the extension to send and receive events to and from the SDK.
     */
    public static void registerExtension() {
        if(MobileCore.getCore() == null || MobileCore.getCore().eventHub == null) {
             Log.warning(LOG_TAG, "Unable to register Analytics Edge SDK since MobileCore is not initialized properly. For more details refer to https://aep-sdks.gitbook.io/docs/using-mobile-extensions/mobile-core");
        }

        MobileCore.registerExtension(AnalyticsEdgeInternal.class, new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {
                Log.debug("There was an error registering Analytics Edge Extension: %s", extensionError.getErrorName());
            }
        });
    }
}
