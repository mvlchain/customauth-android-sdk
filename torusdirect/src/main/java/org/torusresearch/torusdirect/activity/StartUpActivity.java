package org.torusresearch.torusdirect.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.google.gson.GsonBuilder;

import org.torusresearch.torusdirect.R;
import org.torusresearch.torusdirect.interfaces.ILoginHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION;

public class StartUpActivity extends AppCompatActivity {
    public static final String URL = "URL";
    public static final List<String> ALLOWED_CUSTOM_TABS_BROWSERS = Arrays.asList(
            "com.android.chrome", // Chrome stable
            "com.google.android.apps.chrome", // Chrome system
            "com.android.chrome.beta", // Chrome beta
            "com.microsoft.emmx", // Edge stable
            "com.brave.browser", // Brave stable
            "com.brave.browser_beta", // Brave beta
            "com.opera.browser", // Opera stable
            "com.opera.browser.beta", // Opera beta
            "com.vivaldi.browser" // Vivaldi
    );

    public static AtomicReference<ILoginHandler> loginHandler = new AtomicReference<>();
    private final AtomicBoolean isLoginStep = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);
        isLoginStep.set(true);

        String data = getIntent().getStringExtra(URL);
        if (data == null) {
            Log.d("init:torus", "getStringExtra(URL) is NULL!!");
            data = getIntent().getDataString();
        }

        Boolean preferCustomTabs = true;
        String defaultBrowser = getDefaultBrowser();
        List<String> customTabsBrowsers = getCustomTabsBrowsers();

        // Always open default browser in custom tabs if it is supported and whitelisted
        if (customTabsBrowsers.contains(defaultBrowser)) {
            CustomTabsIntent customTabs = new CustomTabsIntent.Builder().build();
            customTabs.intent.setPackage(defaultBrowser);
            customTabs.launchUrl(this, Uri.parse(data));
        } else if (preferCustomTabs && !customTabsBrowsers.isEmpty()) {
            CustomTabsIntent customTabs = new CustomTabsIntent.Builder().build();
            customTabs.intent.setPackage(customTabsBrowsers.get(0));
            customTabs.launchUrl(this, Uri.parse(data));
        } else {
            // No custom tabs, externally in default browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(data)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isLoginStep.get()) {
            setResponse(null);
        } else {
            isLoginStep.set(false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // This is hit because of capturing the redirected url
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) {
            Log.d("result:torus", Objects.requireNonNull(intent.getData()).toString());
            setResponse(intent.getData().toString());
        } else {
            setResponse(null);
        }
    }

    private void setResponse(String response) {
        if (loginHandler != null && loginHandler.get() != null) {
            loginHandler.get().setResponse(response);
            loginHandler.set(null);
        }
        finish();
    }

    private String getDefaultBrowser() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://customauth.io"));
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;
    }

    private List<String> getCustomTabsBrowsers() {
        PackageManager pm = getPackageManager();

        // Loop through whitelisted custom tabs browsers and see if they have CustomTabs service enabled
        List<String> customTabsBrowsers = new ArrayList<>();
        for (String browser : ALLOWED_CUSTOM_TABS_BROWSERS) {
            Intent customTabsIntent = new Intent();
            customTabsIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            customTabsIntent.setPackage(browser);

            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(customTabsIntent, 0) != null) {
                customTabsBrowsers.add(browser);
            }
        }

        return customTabsBrowsers;
    }
}
