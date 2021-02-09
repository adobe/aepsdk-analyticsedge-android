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

class AnalyticsHelper {
    private PlatformServices platformServices;
    private SystemInfoService systemInfoService;

    AnalyticsHelper(final PlatformServices platformServices) {
        this.platformServices = platformServices;
        this.systemInfoService = platformServices.getSystemInfoService();
    }

    /**
     * Gets application info from the {@link AndroidSystemInfoService} and builds an application identifier string.
     *
     */
     String getApplicationIdentifier() {
        final String applicationName =  systemInfoService.getApplicationName();
        final String applicationVersion = systemInfoService.getApplicationVersion();
        final String applicationBuildNumber = systemInfoService.getApplicationVersionCode();
        final StringBuilder applicationIdentifierStringBuilder = new StringBuilder().append(applicationName).append(applicationVersion).append(applicationBuildNumber);
        return applicationIdentifierStringBuilder.toString().replaceAll("  ", " ").replaceAll("()", "").trim();
    }
}
