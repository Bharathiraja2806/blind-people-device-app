param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
  & "$PSScriptRoot\setup.ps1"
  if ($LASTEXITCODE -ne 0) { throw 'Prerequisite checks failed.' }
  if (-not (Test-Path '.\app\src\main\assets\yolo.onnx') -or (Get-Item '.\app\src\main\assets\yolo.onnx').Length -lt 100000) { throw 'Validated model missing. Run the Python export workflow before APK build.' }
  if (-not $SkipTests) { & "$PSScriptRoot\test_all.ps1"; if ($LASTEXITCODE -ne 0) { throw 'Tests failed; refusing to build APK.' } }
  .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
  if ($LASTEXITCODE -ne 0) { throw 'Gradle checks/build failed.' }
  $apk=Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
  if (-not (Test-Path $apk) -or (Get-Item $apk).Length -le 0) { throw 'APK is missing or empty.' }
  $sdk=$env:ANDROID_SDK_ROOT; if (-not $sdk) {$sdk=$env:ANDROID_HOME}
  $aapt=Join-Path $sdk 'build-tools\35.0.0\aapt.exe'
  if (-not (Test-Path $aapt)) { throw "aapt missing: $aapt" }
  & $aapt dump badging $apk | Select-String "package: name='org.blindassist.ai'" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'APK package validation failed.' }
  $out=Join-Path $root 'output'; New-Item -ItemType Directory -Force -Path $out | Out-Null
  $final=Join-Path $out 'BlindAssistAI-debug.apk'; Copy-Item -Force $apk $final
  Write-Host "PASS: APK generated: $([System.IO.Path]::GetFullPath($final))" -ForegroundColor Green
  Write-Host "Size: $((Get-Item $final).Length) bytes"
} catch { Write-Error "FAIL: $($_.Exception.Message)"; exit 1 } finally { Pop-Location }
