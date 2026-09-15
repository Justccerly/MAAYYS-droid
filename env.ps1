# Run: . .\env.ps1
# Refresh this PowerShell session from the installed user environment.
$agentJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$agentAndroidHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
if (!$agentJavaHome -or !(Test-Path -LiteralPath "$agentJavaHome\bin\java.exe")) {
    throw 'Set user JAVA_HOME to an installed JDK 17 directory first.'
}
if (!$agentAndroidHome -or !(Test-Path -LiteralPath "$agentAndroidHome\platform-tools\adb.exe")) {
    throw 'Set user ANDROID_HOME to the installed Android SDK directory first.'
}
$env:JAVA_HOME = $agentJavaHome
$env:ANDROID_HOME = $agentAndroidHome
$env:ANDROID_SDK_ROOT = $agentAndroidHome
$agentToolPaths = @(
    "$agentJavaHome\bin"
    "$agentAndroidHome\platform-tools"
    "$agentAndroidHome\cmdline-tools\16.0\bin"
)
$agentRemainingPaths = @($env:Path -split ';' | Where-Object { $_ -and $_ -notin $agentToolPaths })
$env:Path = ($agentToolPaths + $agentRemainingPaths) -join ';'
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
