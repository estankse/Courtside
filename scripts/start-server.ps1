param([switch]$Background,[int]$Port=8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceTmp = Join-Path (Split-Path -Parent $projectRoot) 'tmp'
$javaPath = Join-Path $workspaceTmp 'toolchain\jdk\bin\java.exe'
if (-not (Test-Path -LiteralPath $javaPath)) { $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe' }
$serverJar = Join-Path $projectRoot 'dist\courtside-server.jar'
if (-not (Test-Path -LiteralPath $serverJar)) { throw '请先执行 scripts/build.ps1 构建服务端' }
$arguments = @('-jar', ('"' + $serverJar + '"'), "--server.port=$Port")
if ($Background) {
    New-Item -ItemType Directory -Path $workspaceTmp -Force | Out-Null
    $serverProcess = Start-Process -FilePath $javaPath -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $workspaceTmp 'server-out.log') -RedirectStandardError (Join-Path $workspaceTmp 'server-error.log') -PassThru
    $serverProcess.Id | Set-Content -LiteralPath (Join-Path $workspaceTmp 'courtside-server.pid')
    Write-Output "服务已启动，进程 $($serverProcess.Id)，端口 $Port"
} else { Push-Location $projectRoot; try { & $javaPath -jar $serverJar "--server.port=$Port" } finally { Pop-Location } }
