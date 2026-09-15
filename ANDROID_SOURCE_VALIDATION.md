# Android Kotlin source validation

Executed `python3 check-android-kotlin.py` using Kotlin 2.0.21, JDK 17 and Robolectric's Android 15 API implementation jar (`org.robolectric:android-all:15-robolectric-12650502`). Download source: Maven Central, https://repo.maven.apache.org/maven2/ . Local jar: /opt/maayys-jvm/android-all-15.jar.

The first compilation found a nullable NotificationManager receiver in MaaAgentForegroundService.onCreate. This was fixed with requireNotNull and a descriptive failure message.

After the fix:
```
library: exit=0
app: exit=0
```
Library and app Kotlin sources were compiled separately, with the app depending on the compiled library jar. Reports and jars: build/android-source-check/ .

The JVM suite was rerun successfully: `OK (25 tests)`, 0.337 seconds.

This is source-level compilation against a Robolectric API jar, not compilation against the official Android SDK public stubs. No Robolectric runner or Android lifecycle test was executed. It does not validate SDK API availability, resource linking, manifest merging, lint, D8/R8, APK signing, installation, foreground-service behavior or native linkage. No APK/AAR was built and no CI workflow was triggered.