package com.blueplanet.conference;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import java.util.Set;

public class MainActivity extends Activity {

    private static final String APP_URL = "https://ocean.ce22resch01004.workers.dev/";
    private static final String AUTH_CALLBACK = "oceanconference://auth/callback";

    private static final int CAMERA_REQUEST_CODE = 6001;
    private static final int MAX_AUTH_INJECTION_ATTEMPTS = 25;

    private WebView webView;
    private ProgressBar progressBar;
    private PermissionRequest pendingWebPermission;

    private String pendingAccessToken;
    private String pendingRefreshToken;
    private String pendingAuthCode;
    private int authInjectionAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);

        configureWebView();
        readAuthCallback(getIntent());

        if (savedInstanceState == null) {
            loadConferenceApp();
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void loadConferenceApp() {
        webView.loadUrl(APP_URL + "?native=android");
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " OceanConferenceAndroid/3.1");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        WebView.setWebContentsDebuggingEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("window.__OCEAN_ANDROID_APP__=true;", null);
                injectSupabaseSessionIfNeeded();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }
        });
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        boolean wantsCamera = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                wantsCamera = true;
                break;
            }
        }

        if (!wantsCamera) {
            request.deny();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingWebPermission = request;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;

        String scheme = uri.getScheme().toLowerCase();

        if ("oceanconference".equals(scheme) || "icmgp2026".equals(scheme)) {
            readAuthUri(uri);
            loadConferenceApp();
            return true;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            openExternal(uri);
            return true;
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (isSupabaseHost(host) && path.contains("/auth/v1/authorize")) {
            Uri oauthUri = replaceQueryParameter(uri, "redirect_to", AUTH_CALLBACK);
            openCustomTab(oauthUri);
            return true;
        }

        Uri appUri = Uri.parse(APP_URL);
        if (appUri.getHost() != null && appUri.getHost().equalsIgnoreCase(host)) {
            return false;
        }

        openExternal(uri);
        return true;
    }

    private boolean isSupabaseHost(String host) {
        return host.endsWith(".supabase.co") || host.equals("supabase.co") || host.endsWith(".supabase.com");
    }

    private Uri replaceQueryParameter(Uri uri, String keyToReplace, String newValue) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        Set<String> names = uri.getQueryParameterNames();
        boolean replaced = false;

        for (String name : names) {
            if (name.equals(keyToReplace)) {
                builder.appendQueryParameter(name, newValue);
                replaced = true;
            } else {
                for (String value : uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, value);
                }
            }
        }

        if (!replaced) builder.appendQueryParameter(keyToReplace, newValue);
        return builder.build();
    }

    private void openCustomTab(Uri uri) {
        try {
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .setToolbarColor(Color.rgb(9, 69, 105))
                    .build();
            tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            tabs.launchUrl(this, uri);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    private void openExternal(Uri uri) {
        try {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                openCustomTab(uri);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (readAuthCallback(intent)) {
            loadConferenceApp();
        }
    }

    private boolean readAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        if (scheme == null ||
                !("oceanconference".equalsIgnoreCase(scheme) || "icmgp2026".equalsIgnoreCase(scheme))) {
            return false;
        }
        readAuthUri(uri);
        return true;
    }

    private void readAuthUri(Uri uri) {
        String access = firstNonEmpty(uri.getQueryParameter("access_token"), fragmentValue(uri, "access_token"));
        String refresh = firstNonEmpty(uri.getQueryParameter("refresh_token"), fragmentValue(uri, "refresh_token"));
        String code = firstNonEmpty(uri.getQueryParameter("code"), fragmentValue(uri, "code"));
        String error = firstNonEmpty(
                firstNonEmpty(uri.getQueryParameter("error_description"), fragmentValue(uri, "error_description")),
                firstNonEmpty(uri.getQueryParameter("error"), fragmentValue(uri, "error"))
        );

        authInjectionAttempts = 0;

        if (access != null && refresh != null) {
            pendingAccessToken = access;
            pendingRefreshToken = refresh;
            pendingAuthCode = null;
        } else if (code != null) {
            pendingAuthCode = code;
            pendingAccessToken = null;
            pendingRefreshToken = null;
        } else if (error != null) {
            clearPendingAuth();
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    "Google returned to the app, but the Supabase session was missing.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String fragmentValue(Uri uri, String wantedKey) {
        String fragment = uri.getFragment();
        if (fragment == null || fragment.isEmpty()) return null;

        for (String pair : fragment.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) continue;
            String key = Uri.decode(pair.substring(0, index));
            if (wantedKey.equals(key)) {
                return Uri.decode(pair.substring(index + 1));
            }
        }
        return null;
    }

    private String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return null;
    }

    private void injectSupabaseSessionIfNeeded() {
        if (pendingAuthCode == null && (pendingAccessToken == null || pendingRefreshToken == null)) return;

        if (authInjectionAttempts++ >= MAX_AUTH_INJECTION_ATTEMPTS) {
            clearPendingAuth();
            Toast.makeText(this,
                    "Could not complete Google login inside the app. Please try again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final String script;

        if (pendingAuthCode != null) {
            String code = javascriptString(pendingAuthCode);
            script = "(async function(){" +
                    "try{" +
                    "if(!window.supabase||!window.APP_CONFIG){return 'not-ready';}" +
                    "const c=window.supabase.createClient(window.APP_CONFIG.SUPABASE_URL,window.APP_CONFIG.SUPABASE_ANON_KEY);" +
                    "const r=await c.auth.exchangeCodeForSession('" + code + "');" +
                    "if(r.error){return 'error:'+r.error.message;}" +
                    "return 'ok';" +
                    "}catch(e){return 'error:'+String(e);}" +
                    "})()";
        } else {
            String access = javascriptString(pendingAccessToken);
            String refresh = javascriptString(pendingRefreshToken);
            script = "(async function(){" +
                    "try{" +
                    "if(!window.supabase||!window.APP_CONFIG){return 'not-ready';}" +
                    "const c=window.supabase.createClient(window.APP_CONFIG.SUPABASE_URL,window.APP_CONFIG.SUPABASE_ANON_KEY);" +
                    "const r=await c.auth.setSession({access_token:'" + access + "',refresh_token:'" + refresh + "'});" +
                    "if(r.error){return 'error:'+r.error.message;}" +
                    "return 'ok';" +
                    "}catch(e){return 'error:'+String(e);}" +
                    "})()";
        }

        webView.evaluateJavascript(script, result -> {
            String text = result == null ? "" : result;

            if (text.contains("ok")) {
                clearPendingAuth();
                CookieManager.getInstance().flush();
                webView.loadUrl(APP_URL + "?native=android&login=complete");
                return;
            }

            if (text.contains("not-ready")) {
                webView.postDelayed(this::injectSupabaseSessionIfNeeded, 500);
                return;
            }

            if (text.contains("error:")) {
                clearPendingAuth();
                Toast.makeText(this,
                        "Google login returned to the app, but Supabase could not save the session.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            webView.postDelayed(this::injectSupabaseSessionIfNeeded, 500);
        });
    }

    private void clearPendingAuth() {
        pendingAccessToken = null;
        pendingRefreshToken = null;
        pendingAuthCode = null;
        authInjectionAttempts = 0;
    }

    private String javascriptString(String raw) {
        if (raw == null) return "";
        return raw
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_REQUEST_CODE || pendingWebPermission == null) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingWebPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingWebPermission.deny();
            Toast.makeText(this,
                    "Camera permission is needed to scan attendee QR codes.",
                    Toast.LENGTH_LONG).show();
        }
        pendingWebPermission = null;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
}
