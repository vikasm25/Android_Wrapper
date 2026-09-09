package com.blueplanet.conference;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class MainActivity extends Activity {

    // This is the hosted full conference web app. Change only this line if your domain changes.
    private static final String APP_URL = "https://ocean.ce22resch01004.workers.dev/";

    // Add this exact URL in Supabase -> Authentication -> URL Configuration -> Redirect URLs.
    private static final String AUTH_CALLBACK = "oceanconference://auth/callback";

    private static final int CAMERA_REQUEST_CODE = 6001;

    private WebView webView;
    private ProgressBar progressBar;
    private PermissionRequest pendingWebPermission;

    private String pendingAccessToken;
    private String pendingRefreshToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);

        configureWebView();

        // If Android launched us from the OAuth callback, remember the tokens first.
        readAuthCallback(getIntent());

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL + "?native=android");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false); // lets in-app notification sounds play
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " OceanConferenceAndroid/1.0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

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
                runOnUiThread(() -> {
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
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;

        String scheme = uri.getScheme().toLowerCase();

        // Deep link from Supabase/Google back into the Android app.
        if ("oceanconference".equals(scheme)) {
            readAuthUri(uri);
            webView.loadUrl(APP_URL + "?native=android&auth_return=1");
            return true;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            openExternal(uri);
            return true;
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();

        // Supabase starts Google OAuth here. Open it in the system browser and replace
        // the webpage redirect with our Android deep-link callback. This avoids Google's
        // embedded-WebView OAuth restriction.
        if (host.endsWith(".supabase.co") && path.contains("/auth/v1/authorize")) {
            Uri externalAuth = replaceQueryParameter(uri, "redirect_to", AUTH_CALLBACK);
            openExternal(externalAuth);
            return true;
        }

        // Keep the actual conference site inside the WebView.
        Uri appUri = Uri.parse(APP_URL);
        if (appUri.getHost() != null && appUri.getHost().equalsIgnoreCase(host)) {
            return false;
        }

        // Maps, sponsor sites, mail links, etc. open in the normal phone browser/app.
        openExternal(uri);
        return true;
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

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (readAuthCallback(intent)) {
            webView.loadUrl(APP_URL + "?native=android&auth_return=1");
        }
    }

    private boolean readAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        if (!"oceanconference".equalsIgnoreCase(uri.getScheme())) return false;
        readAuthUri(uri);
        return true;
    }

    private void readAuthUri(Uri uri) {
        String access = firstNonEmpty(uri.getQueryParameter("access_token"), fragmentValue(uri, "access_token"));
        String refresh = firstNonEmpty(uri.getQueryParameter("refresh_token"), fragmentValue(uri, "refresh_token"));
        String error = firstNonEmpty(uri.getQueryParameter("error_description"), fragmentValue(uri, "error_description"));

        if (access != null && refresh != null) {
            pendingAccessToken = access;
            pendingRefreshToken = refresh;
        } else if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    "Google returned to the app, but no Supabase session token was found.",
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

    // The OAuth browser and this WebView have separate storage. We copy the returned
    // Supabase access/refresh tokens into the webpage's normal Supabase local session,
    // then reload the site. The existing website code continues to handle everything.
    private void injectSupabaseSessionIfNeeded() {
        if (pendingAccessToken == null || pendingRefreshToken == null) return;

        String access = javascriptString(pendingAccessToken);
        String refresh = javascriptString(pendingRefreshToken);

        String script = "(async function(){" +
                "try{" +
                "if(!window.supabase||!window.APP_CONFIG){return 'not-ready';}" +
                "const c=window.supabase.createClient(window.APP_CONFIG.SUPABASE_URL,window.APP_CONFIG.SUPABASE_ANON_KEY);" +
                "const r=await c.auth.setSession({access_token:'" + access + "',refresh_token:'" + refresh + "'});" +
                "if(r.error){return 'error:'+r.error.message;}" +
                "return 'ok';" +
                "}catch(e){return 'error:'+String(e);}" +
                "})()";

        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("ok")) {
                pendingAccessToken = null;
                pendingRefreshToken = null;
                webView.loadUrl(APP_URL + "?native=android");
            } else if (result != null && result.contains("error:")) {
                Toast.makeText(this, "Could not save Google session in app.", Toast.LENGTH_LONG).show();
            } else {
                // JS libraries/config may still be loading. onPageFinished/reload will retry.
                webView.postDelayed(this::injectSupabaseSessionIfNeeded, 700);
            }
        });
    }

    private String javascriptString(String raw) {
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
            Toast.makeText(this, "Camera permission is needed to scan attendee QR codes.", Toast.LENGTH_LONG).show();
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
