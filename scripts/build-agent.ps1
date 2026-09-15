param([string[]]$Abis = @('arm64-v8a', 'x86_64'))
$ErrorActionPreference = 'Stop'
$agentProject = Split-Path $PSScriptRoot -Parent
$agentSdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$agentGo = Join-Path $env:LOCALAPPDATA 'Programs\go\bin\go.exe'
$agentCompilerDir = Join-Path $agentSdk 'ndk\27.2.12479018\toolchains\llvm\prebuilt\windows-x86_64\bin'
$agentOldEnv = @{}
foreach ($agentName in @('GOOS','GOARCH','CGO_ENABLED','CC','GOPROXY')) { $agentOldEnv[$agentName] = [Environment]::GetEnvironmentVariable($agentName, 'Process') }
Push-Location (Join-Path $agentProject 'upstream\maayys\agent')
try {
    $env:GOPROXY = 'https://goproxy.cn,https://proxy.golang.org,direct'
    $env:GOOS = 'android'
    $env:CGO_ENABLED = '1'
    foreach ($agentAbi in $Abis) {
        switch ($agentAbi) {
            'arm64-v8a' { $env:GOARCH = 'arm64'; $agentCompiler = 'aarch64-linux-android26-clang.cmd' }
            'x86_64' { $env:GOARCH = 'amd64'; $agentCompiler = 'x86_64-linux-android26-clang.cmd' }
            default { throw "Unsupported ABI: $agentAbi" }
        }
        $env:CC = Join-Path $agentCompilerDir $agentCompiler
        $agentOutput = Join-Path $agentProject "app\src\main\jniLibs\$agentAbi\libmaayys_agent.so"
        New-Item -ItemType Directory -Force (Split-Path $agentOutput) | Out-Null
        & $agentGo build -trimpath -ldflags '-s -w -extldflags=-Wl,-z,max-page-size=16384' -o $agentOutput .
        if ($LASTEXITCODE -ne 0) { throw "Agent build failed: $agentAbi" }
        Write-Host "Built MaaYYs Agent: $agentAbi"
    }
} finally {
    Pop-Location
    foreach ($agentName in $agentOldEnv.Keys) { [Environment]::SetEnvironmentVariable($agentName,$agentOldEnv[$agentName],'Process') }
}
