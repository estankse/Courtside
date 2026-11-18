[CmdletBinding()]
param([string]$Purpose = 'Android debugging or debug APK packaging')

$windowsAccount = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$accountUser = ($windowsAccount -split '\\')[-1]
if ($accountUser -eq 'CodexSandboxOffline') {
    throw "Cannot run $Purpose as $windowsAccount. Use the Windows account running Android Studio to preserve ADB authorization and debug APK signing identity."
}