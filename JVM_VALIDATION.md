# Local JVM validation

## Executed result
Kotlin 2.0.21 compilation and JUnit 4.13.2 execution succeeded in the local ARM64 Ubuntu environment:

```
JUnit version 4.13.2
.........................
Time: 0.328
OK (25 tests)
```

Run: `python3 run-jvm-tests.py` from this module, or provide the script's absolute path.
Report: `build/jvm-validation/result.txt`; compiled test jar: `build/jvm-validation/tests.jar`.

OpenJDK 17 was installed using Ubuntu apt. Compiler dependencies were downloaded from `https://repo.maven.apache.org/maven2/` into `/opt/maayys-jvm` (outside the repository). Override this directory with MAAYYS_JVM_TOOLS.
Jars: kotlin-compiler-embeddable, kotlin-stdlib and kotlin-script-runtime 2.0.21; kotlin-reflect 1.6.10; trove4j 1.0.20200330; kotlinx-coroutines-core-jvm 1.6.4; annotations 13.0; junit 4.13.2; hamcrest-core 1.3.

## Scope
Compiled agent/resource/runtime Kotlin sources and the JVM-only host/factory/selector classes. Executed 7 RootController, 3 RandomTouchAction, 4 ControllerSelector, 5 SafeResourceZip and 6 ResourceProvider tests.
Root command execution and resource downloads were replaced by test implementations. No Android root authorization, input injection or real network download was tested.

## Not validated
Android Activity/Service/Application classes, SharedPreferences, manifest merging, Android lint, APK/AAR packaging, native JNI linkage, native lifecycle or actual Shizuku/ADB/Root integration. Gradle/Android SDK build and CI execution remain pending.
This JVM run compiles main and test sources together; it is not a replacement for Android Gradle's separate compilation and packaging checks.

Earlier progress documents stating all tests are pending are superseded by this result for these 25 JVM tests only.