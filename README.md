# Ocean Conference Android Wrapper

This repository builds the Android wrapper for the hosted attendee app:

`https://ocean.ce22resch01004.workers.dev/`

## What this wrapper does

- Keeps the full conference attendee application inside the Android WebView.
- Uses a secure Chrome Custom Tab only for Google sign-in, because Google blocks OAuth inside embedded WebViews.
- After Google/Supabase authentication completes, `oceanconference://auth/callback` returns control to the Android app and the wrapper transfers the Supabase session into the WebView.
- Supports both Supabase implicit-token and PKCE-code OAuth returns.
- Keeps profile, schedule, networking, chat, announcements, sponsors and other app pages inside the APK.
- Supports camera permission for QR scanning.
- Allows notification/message sounds from the webpage.
- Uses the conference launcher icon and Android splash screen.

## Required Supabase setting

In Supabase open:

**Authentication -> URL Configuration -> Redirect URLs**

Make sure this exact URL exists:

`oceanconference://auth/callback`

Keep the normal web app URL there too:

`https://ocean.ce22resch01004.workers.dev/`

Google Cloud should continue using the normal Supabase callback:

`https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`

Do not add the Android custom scheme to Google Cloud; it belongs in Supabase Redirect URLs.

## Google login behavior

Google's credential screen briefly appears in a secure Chrome Custom Tab. That is required by Google. After login finishes, control returns automatically to the Android app; the conference content itself stays inside the APK.

## Build APK

Every push to `main` runs **Build Android APK** automatically. You can also run it manually from **Actions -> Build Android APK -> Run workflow**.

After the build succeeds, download the artifact named:

`icmgp-2026-debug-apk`

Inside it is:

`app-debug.apk`

## Current application ID

`com.blueplanet.conference`
