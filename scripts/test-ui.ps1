param([string]$Serial='emulator-5556',[switch]$UseAdbReverse)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$workspaceTmp=Join-Path (Split-Path -Parent $projectRoot) 'tmp'
$sdkLine=Get-Content -LiteralPath (Join-Path $projectRoot 'local.properties') | Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
if(-not $sdkLine){throw '请先配置 local.properties 中的 sdk.dir'}
$sdkPath=$sdkLine.Substring(8).Replace('\:',':').Replace('\\','\')
$adbPath=Join-Path $sdkPath 'platform-tools\adb.exe'
$jdkPath=Join-Path $workspaceTmp 'toolchain\jdk'
if(Test-Path -LiteralPath $jdkPath){$env:JAVA_HOME=$jdkPath}
if(-not $env:JAVA_HOME){throw '请先设置 JAVA_HOME 为 JDK 17 或 21，或执行 scripts/setup.ps1'}
$env:GRADLE_USER_HOME=Join-Path $workspaceTmp 'gradle-home'
$gradlePath=Join-Path $workspaceTmp 'toolchain\gradle-8.11.1\bin\gradle.bat'
if(-not (Test-Path -LiteralPath $gradlePath)){$gradlePath=Join-Path $projectRoot 'gradlew.bat'}
Push-Location $projectRoot
try{
    & $gradlePath --project-cache-dir (Join-Path $workspaceTmp 'gradle-project-cache') :client:assembleDebugAndroidTest --console=plain
    if($LASTEXITCODE -ne 0){throw '界面测试包构建失败'}
    $serviceUrl='http://10.0.2.2:8080/api'
    if($UseAdbReverse){& $adbPath -s $Serial reverse tcp:8080 tcp:8080;$serviceUrl='http://127.0.0.1:8080/api'}
    & $adbPath -s $Serial install -r (Join-Path $workspaceTmp 'builds\client\outputs\apk\androidTest\debug\client-debug-androidTest.apk')
    if($LASTEXITCODE -ne 0){throw '测试包安装失败'}
    $testResult=& $adbPath -s $Serial shell am instrument -w -e baseUrl $serviceUrl -e class cn.courtside.client.WorkflowUiTest cn.courtside.client.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $testResult | Tee-Object -FilePath (Join-Path $workspaceTmp 'ui-test-result.log')
    $imageDirectory=Join-Path $workspaceTmp 'ui-screenshots-latest'
    New-Item -ItemType Directory -Path $imageDirectory -Force | Out-Null
    & $adbPath -s $Serial pull /sdcard/Android/data/cn.courtside.client.test/files/screenshots $imageDirectory
    if(($testResult -join [Environment]::NewLine) -notmatch 'OK \(1 test\)'){throw '界面流程未通过，详见 tmp/ui-test-result.log'}
}finally{Pop-Location}
