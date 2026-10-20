param([switch]$ServerOnly,[switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceTmp = Join-Path (Split-Path -Parent $projectRoot) 'tmp'
$jdkPath = Join-Path $workspaceTmp 'toolchain\jdk'
if (Test-Path -LiteralPath $jdkPath) { $env:JAVA_HOME = $jdkPath }
if (-not $env:JAVA_HOME) { throw '请先设置 JAVA_HOME 为 JDK 17 或 21，或执行 scripts/setup.ps1' }
$env:GRADLE_USER_HOME = Join-Path $workspaceTmp 'gradle-home'
$gradlePath = Join-Path $workspaceTmp 'toolchain\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradlePath)) { $gradlePath = Join-Path $projectRoot 'gradlew.bat' }
$gradleTasks = @(':server:bootJar')
if (-not $ServerOnly) { $gradleTasks += @(':organizer:assembleDebug',':referee:assembleDebug') }
if (-not $SkipTests) { $gradleTasks += @(':shared:test',':server:test') }
$gradleOptions = @('--project-cache-dir',(Join-Path $workspaceTmp 'gradle-project-cache'),'--console=plain')
if ($ServerOnly) { $gradleOptions += '-PserverOnly' }
Push-Location $projectRoot
try { & $gradlePath @gradleOptions @gradleTasks; if ($LASTEXITCODE -ne 0) { throw '构建失败，请检查上方日志' } } finally { Pop-Location }
$distPath = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $distPath -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $workspaceTmp 'builds\server\libs\server-1.0.0.jar') -Destination (Join-Path $distPath 'courtside-server.jar')
if (-not $ServerOnly) {
    Copy-Item -LiteralPath (Join-Path $workspaceTmp 'builds\organizer\outputs\apk\debug\organizer-debug.apk') -Destination (Join-Path $distPath 'courtside-organizer.apk')
    Copy-Item -LiteralPath (Join-Path $workspaceTmp 'builds\referee\outputs\apk\debug\referee-debug.apk') -Destination (Join-Path $distPath 'courtside-referee.apk')
}
Write-Output "构建完成，安装包位于 $distPath"
