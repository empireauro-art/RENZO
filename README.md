# RENZO ID Scanner

A minimal native Android app (Java, Android SDK only) for displaying launcher apps, classifying each as GAME / APP / UNKNOWN, checking root access on explicit request, detecting known attribution SDK code bundled in installed APKs, and receiving diagnostic records explicitly exported by apps the user owns or is authorized to test.

## What the scanner does

- Four-column grid of launcher application icons; application names are not visibly shown.
- A GAME / APP / UNKNOWN label appears below each icon.
- Detects known AppsFlyer, Adjust, and Singular class signatures by inspecting the public DEX files inside each launcher's installed APK and split APKs. Multiple provider labels can appear under one app.
- Provider labels detected from APK code are shown in blue. A label with a separately exported diagnostic record is shown in white; tap that label to copy the exact stored record.
- A ROOT status line appears at the top. Tap **CHECK ROOT** to explicitly request a Superuser decision and check whether `su -c id` returns UID 0. Root is not requested on app launch.
- Diagnostic records explicitly exported by an authorized source app remain local in app-private SharedPreferences.
- No Internet permission, ads, analytics, or external UI dependencies.

## Important detection and Android limitations

The SDK scan looks only for known SDK class descriptors in the installed APK's DEX bytecode. It does not read another app's private `shared_prefs`, databases, files, runtime memory, or device/user identifiers. Root is not used by this scan and is not required for it.

A detected label means a matching SDK class is bundled in the APK; it does not prove that the SDK is initialized, active, or responsible for a particular network request. Detection can miss SDKs if code is heavily obfuscated, dynamically delivered, repackaged, or stripped by the build. It may also find an SDK that was included but is not used. Apps without a launcher icon are not included in the grid.

A provider label is not an extracted identifier. Values such as AppsFlyer IDs, Adjust ad IDs, Singular IDs, GAID, and Android ID are not collected from unrelated apps. For diagnostics in an app you own or are authorized to test, that source app must obtain values through its official SDK APIs and explicitly export the diagnostic record. This scanner does not bundle the vendor SDKs.

## Export contract for an authorized source app

Send an explicit `ACTION_SEND` intent with the original record in `Intent.EXTRA_TEXT`, the provider in the extra `com.renzo.idscanner.extra.PROVIDER`, and launch RENZO with `startActivityForResult` so Android provides the calling package to the receiver. Supported provider values are `appsflyer`, `adjust`, and `singular`.

```java
Intent i = new Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, exactDiagnosticRecord)
    .putExtra("com.renzo.idscanner.extra.PROVIDER", "appsflyer")
    .setPackage("com.renzo.idscanner");
startActivityForResult(i, 1001);
```

The receiver rejects exports when Android does not provide a verified calling package. It does not trust a package name typed into the payload or an unverified referrer value.

## Build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0, and Gradle 8.7.

```bash
gradle --no-daemon :app:assembleDebug :app:assembleRelease :sample:assembleDebug
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `sample/build/outputs/apk/debug/sample-debug.apk`

Release uses the personal key configured in `keystore.properties` when supplied. Otherwise it is a debug-signed test build, not a production release.

## Test the non-Android validation logic

```bash
mkdir -p /tmp/renzo-tests
javac -d /tmp/renzo-tests \
  app/src/main/java/com/renzo/idscanner/Provider.java \
  app/src/main/java/com/renzo/idscanner/RecordFormat.java \
  app/src/main/java/com/renzo/idscanner/ProviderSignatureDetector.java \
  app/src/main/java/com/renzo/idscanner/SdkScanner.java \
  tools/test/RecordFormatTest.java \
  tools/test/ProviderSignatureDetectorTest.java \
  tools/test/SdkScannerTest.java
java -cp /tmp/renzo-tests com.renzo.idscanner.RecordFormatTest
java -cp /tmp/renzo-tests com.renzo.idscanner.ProviderSignatureDetectorTest
java -cp /tmp/renzo-tests com.renzo.idscanner.SdkScannerTest
```

## End-to-end test

1. Install the scanner and sample exporter APKs.
2. Open RENZO ID Scanner. Apps whose installed APK code contains a recognized AppsFlyer, Adjust, or Singular class signature should show the corresponding blue provider label under the icon.
3. Apps with more than one recognized SDK should show all matching labels.
4. Open **RENZO Sample Exporter** and export each clearly labeled test fixture. The sample exporter's icon should then show exported-record labels in white.
5. Tap a white provider label and verify the exact corresponding fixture text appears on the clipboard.
6. Check that GAME / APP classification appears under launcher icons.
7. Tap **CHECK ROOT** and respond to the Superuser prompt if the device has a root manager. Verify the status reflects the actual result.

The Android app must still be compiled and tested on an emulator or physical Android device before calling the APK complete.
