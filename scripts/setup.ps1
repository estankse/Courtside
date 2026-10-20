$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path (Split-Path -Parent $projectRoot) 'tmp\toolchain'
New-Item -ItemType Directory -Path $toolsRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot 'jdk'))) {
    $metadata = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $metadata[0].binary.package
    $archive = Join-Path $toolsRoot 'jdk.zip'
    Invoke-WebRequest $package.link -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum) { throw 'JDK 校验失败' }
    $extract = Join-Path $toolsRoot 'jdk-extracted'
    Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
    $folder = Get-ChildItem -LiteralPath $extract -Directory | Select-Object -First 1
    Copy-Item -LiteralPath $folder.FullName -Destination (Join-Path $toolsRoot 'jdk') -Recurse
}
if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot 'gradle-8.11.1'))) {
    $archive = Join-Path $toolsRoot 'gradle.zip'
    Invoke-WebRequest 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $archive
    $response = (Invoke-WebRequest 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Content
    $digest = if ($response -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response).Trim() } else { $response.Trim() }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $digest) { throw 'Gradle 校验失败' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
}
Write-Output '工具链已准备。请在 Android Studio 中配置 Android 35 SDK 和 36.0.0 Build Tools，并设置 local.properties。'
