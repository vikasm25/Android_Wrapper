# ICMGP 2026 Android Wrapper

This repository builds the Android wrapper for the hosted attendee app:

`https://icmgp-2026-app.vercel.app/`

## What this wrapper does

- Keeps the full ICMGP attendee application inside the Android WebView.
- Uses a secure Chrome Custom Tab only for Google sign-in, because Google blocks OAuth inside embedded WebViews.
- After Google/Supabase finishes authentication, `oceanconference://auth/callback` reopens the Android app and the wrapper transfers the Supabase session into the WebView.
- Supports both Supabase implicit-token and PKCE-code OAuth returns.
- Keeps profile, schedule, networking, chat, announcements, sponsors and other ICMGP app pages inside the APK.
- Supports camera permission for QR scanning.
- Allows notification/message sounds from the webpage.
- Uses an ICMGP-style ocean/mercury launcher icon and Android 12+ splash screen.

## Required Supabase setting

In Supabase open:

**Authentication -> URL Configuration -> Redirect URLs**

Make sure this exact URL exists:

`oceanconference://auth/callback`

Keep the normal web app URL there too:

`https://icmgp-2026-app.vercel.app/`

Google Cloud itself should continue using the normal Supabase callback:

`https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`

Do not add the Android custom scheme to Google Cloud; it belongs in Supabase Redirect URLs.

## Google login behavior

Google's credential screen briefly appears in a Chrome Custom Tab. That is intentional and required for secure Google OAuth. When login finishes, the Custom Tab hands control back to the Android app. The attendee app itself should not remain open in Chrome.

## Build APK

Every push to `main` runs **Build Android APK** automatically. You can also run it manually from **Actions -> Build Android APK -> Run workflow**.

After the build succeeds, download the artifact named:

`icmgp-2026-debug-apk`

Inside it is:

`app-debug.apk`

## Current application ID

`com.blueplanet.conference`

It is intentionally unchanged from earlier test APKs so this APK can update the already-installed test app rather than installing as a second application.
