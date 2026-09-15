# Root controller validation

## Current status
RootController uses RootCommandExecutor injection; production defaults to RootShell (su -c).
Seven JUnit tests have been added, but have NOT been executed in the current development environment.
Environment probe: java, gradle and kotlinc were not found on PATH; ANDROID_HOME was unset.
No real-device root authorization, input injection or screenshot was performed.

## Local unit tests
Provide JDK 17, Gradle 8.9, Android SDK platform 35 and build tools 34.0.0. The module uses AGP 8.7.3 and Kotlin 2.0.21.
From this module's directory run `gradle assembleDebug testDebugUnitTest lintDebug`.
There is currently no Gradle wrapper. The repository-root workflow `.github/workflows/android-agent.yml` provisions the tools on an Ubuntu GitHub runner and uploads test/lint reports and AAR outputs. It has been written but not triggered or verified here; it requires publishing this workspace as a repository with Actions enabled.
Three additional RandomTouchAction tests cover single-pixel ROI, coordinate bounds and overflow rejection. Total tests added so far: ten, all pending execution.
The tests inject fake command results and never invoke su. Coverage includes UID/exit status, missing su, denied commands, empty successful output, invalid arguments, PNG signature, launch confirmation and interruption.
The PNG fixture validates only the transport signature, not full image integrity.

## Host integration
The host Application implements AgentDependencies. When the user explicitly selects Root, its createAgentService implementation should return RootAgentFactory.create(resources, runtime).
Do not automatically select Root or fall back to it after Shizuku failure.
RootAgentFactory requests authorization on the foreground service command worker; the runtime must be supplied by the real host.
Root authorization is managed by an installed root manager, not an Android manifest permission.

## Pending device checks
- Grant, deny, revoke and authorization timeout behavior in the installed root manager.
- Tap/swipe on an explicitly selected test screen; orientation and coordinate mapping.
- Decode screenshot bytes and verify image dimensions; protected surfaces may remain unavailable.
- Launch an installed test package and reject a missing package.
- Stop during a command and verify child-process/thread cleanup (destroying su does not guarantee every descendant exits on every implementation).
- Foreground service startup restrictions, notification permission and service destruction.
- Native controller and screenshot callbacks: still pending JNI integration.
