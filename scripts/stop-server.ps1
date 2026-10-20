$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path (Split-Path -Parent $projectRoot) 'tmp\courtside-server.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Output '没有本脚本启动的后台服务记录'; exit 0 }
$serverProcessId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
$serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $serverProcessId"
$expectedJar = Join-Path $projectRoot 'dist\courtside-server.jar'
if ($null -ne $serverProcess -and $serverProcess.Name -eq 'java.exe' -and $serverProcess.CommandLine.Contains($expectedJar)) {
    [Diagnostics.Process]::GetProcessById($serverProcessId).Kill()
    Write-Output "已停止羽序后台服务 $serverProcessId"
} else { Write-Output '服务已停止或进程不匹配，未处理其他进程' }
