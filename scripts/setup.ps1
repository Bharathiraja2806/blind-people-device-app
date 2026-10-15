$ErrorActionPreference = 'Stop'
$failed = $false
function Check-Command([string]$Name, [string]$Hint) {
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  if ($cmd) { Write-Host "FOUND $Name -> $($cmd.Source)" -ForegroundColor Green }
  else { Write-Host "MISSING $Name. $Hint" -ForegroundColor Red; $script:failed = $true }
}
Check-Command 'java' 'Install a JDK 17 and set JAVA_HOME/PATH.'
Check-Command 'python' 'Install Python 3.10 or newer and add it to PATH.'
Check-Command 'adb' 'Install Android SDK Platform Tools and add platform-tools to PATH.'
if (Get-Command java -ErrorAction SilentlyContinue) {
  $versionLine = (& java -version 2>&1 | Select-Object -First 1) -join ''
  if ($versionLine -match 'version "([0-9]+)') {
    if ([int]$Matches[1] -lt 17) { Write-Host "JDK 17+ required; detected $versionLine" -ForegroundColor Red; $failed = $true }
    else { Write-Host "Java version: $versionLine" -ForegroundColor Green }
  } else { Write-Host 'Could not determine Java version; verify JDK 17 or newer.' -ForegroundColor Red; $failed = $true }
}
if (Get-Command python -ErrorAction SilentlyContinue) {
  python -c "import sys; raise SystemExit(0 if sys.version_info >= (3,10) else 1)"
  if ($LASTEXITCODE -ne 0) { Write-Host 'Python 3.10 or newer is required.' -ForegroundColor Red; $failed = $true }
}
$wrapperRoot = Join-Path $PSScriptRoot '..\gradle\wrapper'
if (-not (Test-Path (Join-Path $PSScriptRoot '..\gradlew.bat')) -or -not (Test-Path (Join-Path $wrapperRoot 'gradle-wrapper.jar')) -or -not (Test-Path (Join-Path $wrapperRoot 'gradle-wrapper.properties'))) { Write-Host 'MISSING Gradle wrapper files. Restore wrapper jar/properties or generate them with Gradle 8.9.' -ForegroundColor Red; $failed = $true } else { Write-Host 'FOUND Gradle 8.9 wrapper' -ForegroundColor Green }
$sdk = $env:ANDROID_SDK_ROOT; if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk -or -not (Test-Path $sdk)) { Write-Host 'MISSING Android SDK. Set ANDROID_SDK_ROOT to its installed directory.' -ForegroundColor Red; $failed = $true }
else {
  Write-Host "Android SDK: $sdk"
  if (-not (Test-Path (Join-Path $sdk 'platforms\android-35'))) { Write-Host 'MISSING SDK platform android-35. Install with sdkmanager "platforms;android-35".' -ForegroundColor Red; $failed = $true }
  if (-not (Test-Path (Join-Path $sdk 'build-tools\35.0.0'))) { Write-Host 'MISSING build-tools 35.0.0. Install with sdkmanager "build-tools;35.0.0".' -ForegroundColor Red; $failed = $true }
}
if ($failed) { Write-Error 'Setup prerequisites are incomplete. See README.md for command-line installation steps.'; exit 1 }
$venvPython = Join-Path (Split-Path -Parent $PSScriptRoot) '.venv\Scripts\python.exe'
if (-not (Test-Path $venvPython)) { python -m venv (Join-Path (Split-Path -Parent $PSScriptRoot) '.venv'); if ($LASTEXITCODE -ne 0) { throw 'Could not create Python virtual environment.' } }
& $venvPython -m pip install -r (Join-Path (Split-Path -Parent $PSScriptRoot) 'requirements-export.txt')
if ($LASTEXITCODE -ne 0) { throw 'Python export dependency installation failed.' }
Write-Host 'PASS: required command-line build tools detected.' -ForegroundColor Green
