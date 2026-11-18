[CmdletBinding()]
param([string]$SdkRoot = $env:ANDROID_HOME)

$ErrorActionPreference = 'Stop'
$windowsAccount = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$accountUser = ($windowsAccount -split '\\')[-1]
& (Join-Path $PSScriptRoot 'assert-android-account.ps1') -Purpose 'ADB repair'

if ([string]::IsNullOrWhiteSpace($SdkRoot)) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($SdkRoot)) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) {
    throw '找不到 adb.exe。请设置 ANDROID_HOME，或使用 -SdkRoot 指定 Android SDK 目录。'
}

Write-Output "当前 Windows 账号：$windowsAccount"
& $adbPath kill-server
if ($LASTEXITCODE -ne 0) { throw '停止旧 ADB 后台失败。' }
& $adbPath start-server
if ($LASTEXITCODE -ne 0) { throw '启动 ADB 后台失败。' }
Start-Sleep -Seconds 1

$listener = Get-NetTCPConnection -State Listen -LocalPort 5037 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $listener) {
    $serverProcess = Get-CimInstance Win32_Process -Filter ("ProcessId = " + $listener.OwningProcess)
    if ($null -ne $serverProcess) {
        $serverOwner = Invoke-CimMethod -InputObject $serverProcess -MethodName GetOwner
        if ($serverOwner.ReturnValue -eq 0) {
            Write-Output "ADB 后台账号：$($serverOwner.Domain)\$($serverOwner.User)"
            if ($serverOwner.User -ine $accountUser) {
                throw 'ADB 又被其他账号启动，请先关闭该账号下正在使用 ADB 的工具，再重试。'
            }
        }
    }
}

& $adbPath devices -l
if ($LASTEXITCODE -ne 0) { throw '读取 ADB 设备状态失败。' }
Write-Output '请在 Android Studio 中重新运行项目。若设备仍显示 unauthorized，请在模拟器中手动确认调试授权。'
