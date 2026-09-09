# Ocean Conference Android Wrapper v2

This is a real Android Studio project that renders the hosted conference web app inside a secure WebView.

## What is fixed in v2

- Includes the missing GitHub Actions workflow: `.github/workflows/build-android-apk.yml`
- Builds a signed debug APK that can be installed directly on Android phones
- Loads the full hosted app, not a demo/preview copy
- Handles camera permission for QR scanning
- Allows in-app notification sounds
- Opens Google OAuth in the system browser instead of the embedded WebView
- Returns Google/Supabase authentication to the Android app using `oceanconference://auth/callback`
- Opens Google Maps and external sponsor links outside the WebView
- HTTPS-only network policy

## Before building

### 1. Confirm your live web app URL

The Android project currently loads:

`https://ocean.ce22resch01004.workers.dev/`

If your final site URL changes, edit this line in:

`app/src/main/java/com/blueplanet/conference/MainActivity.java`

```java
private static final String APP_URL = "https://YOUR-DOMAIN/";
```

### 2. Supabase redirect URL

In Supabase open:

Authentication -> URL Configuration -> Redirect URLs

Add this exact redirect URL:

`oceanconference://auth/callback`

Keep your normal HTTPS website URL there as well.

### 3. Google provider

Keep the normal Google OAuth callback in Google Cloud:

`https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`

Do NOT put `oceanconference://auth/callback` in Google Cloud. It belongs in Supabase Redirect URLs.

## Build the APK on GitHub for free

1. Create a new GitHub repository.
2. Upload/push the CONTENTS of this folder to the repository root.
3. Make sure `.github/workflows/build-android-apk.yml` exists in GitHub.
4. Open the repository's **Actions** tab.
5. Select **Build Android APK**.
6. Click **Run workflow**.
7. After the green build finishes, open the workflow run.
8. Under **Artifacts**, download `ocean-conference-debug-apk`.
9. Unzip it to get `app-debug.apk`.

The debug APK is automatically signed by Android's debug signing system and is suitable for direct testing/installing.

## If the workflow does not appear

Check that the file is exactly here in the repository:

`.github/workflows/build-android-apk.yml`

A second visible copy is included as `WORKFLOW-COPY-build-android-apk.yml`, but GitHub only recognizes the copy inside `.github/workflows/`.

## Android Studio build

You can also open the project folder in Android Studio and choose:

Build -> Build App Bundle(s) / APK(s) -> Build APK(s)

Android Studio will download the required Gradle/SDK components.

## Security

This wrapper does not contain your Supabase service-role key, database password, or Google client secret. It renders your HTTPS website and uses the publishable frontend configuration already hosted with that site.
