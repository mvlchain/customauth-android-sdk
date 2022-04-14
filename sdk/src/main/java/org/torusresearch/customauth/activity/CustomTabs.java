// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.torusresearch.customauth.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.browser.customtabs.CustomTabsIntent;

import org.torusresearch.customauth.BuildConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomTabs {

    public static void openCustomTab(
            @NonNull Activity activity,
            @Nullable String targetPackage,
            @NonNull Uri uri,
            @Nullable CustomTabFallback fallback
    ) {
        String packageName = !TextUtils.isEmpty(targetPackage) ?
                targetPackage : CustomTabsHelper.getPackageNameToUse(activity);

        if (packageName != null) {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            customTabsIntent.intent.setPackage(targetPackage);
            try {
                customTabsIntent.launchUrl(activity, uri);
            } catch (ActivityNotFoundException e) {
                new CustomTabsIntent.Builder().build().launchUrl(activity, uri);
            }
        } else {
            if (fallback != null) {
                fallback.openUri(activity, uri);
            }
        }
    }

    /**
     * Opens the URL on a Custom Tab if possible. Otherwise fallback to opening it on a WebView.
     * <p>
     * ref: https://github.com/GoogleChrome/android-browser-helper/blob/main/demos/custom-tabs-example-app/src/main/java/org/chromium/customtabsdemos/CustomTabActivityHelper.java
     *
     * @param activity The host activity.
     * @param customTabsIntent      a URI to be wrapped with CustomTabsIntent to be used if Custom Tabs is available.
     * @param uri      the Uri to be opened.
     * @param fallback a CustomTabFallback to be used if Custom Tabs is not available.
     */
    public static void openCustomTab(
            @NonNull Activity activity,
            @NonNull CustomTabsIntent customTabsIntent,
            @NonNull Uri uri,
            @Nullable CustomTabFallback fallback
    ) {
        boolean launched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                openNonBrowserUriApi30(activity, uri) :
                openNonBrowserUriBeforeApi30(activity, uri);

        if (!launched) {
            String packageName = CustomTabsHelper.getPackageNameToUse(activity);
            if (packageName != null) {
                customTabsIntent.intent.setPackage(packageName);
                customTabsIntent.launchUrl(activity, uri);
            } else {
                if (fallback != null) {
                    fallback.openUri(activity, uri);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    static boolean openNonBrowserUriApi30(Context context, Uri uri) {
        Intent nativeAppIntent = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
        try {
            context.startActivity(nativeAppIntent);
            return true;
        } catch (ActivityNotFoundException ex) {
            return false;
        }
    }

    private static boolean openNonBrowserUriBeforeApi30(Context context, Uri uri) {
        PackageManager pm = context.getPackageManager();

        // Get all Apps that resolve a generic url
        Intent browserActivityIntent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.fromParts("http", "", null));
        Set<String> genericResolvedList = extractPackageNames(
                pm.queryIntentActivities(browserActivityIntent, 0));

        // Get all apps that resolve the specific Url
        Intent specializedActivityIntent = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        Set<String> resolvedSpecializedList = extractPackageNames(
                pm.queryIntentActivities(specializedActivityIntent, 0));

        // Keep only the Urls that resolve the specific, but not the generic
        // urls.
        resolvedSpecializedList.removeAll(genericResolvedList);

        // If the list is empty, no native app handlers were found.
        if (resolvedSpecializedList.isEmpty()) {
            return false;
        }

        // We found native handlers. Launch the Intent.
        specializedActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(specializedActivityIntent);
        return true;
    }

    private static Set<String> extractPackageNames(List<ResolveInfo> resolveInfoList) {
        HashSet<String> result = new HashSet<>();
        for (ResolveInfo ri : resolveInfoList) {
            if (ri.activityInfo != null) {
                result.add(ri.activityInfo.packageName);
            }
        }
        return result;
    }

}
