$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$results = [System.Collections.Generic.List[object]]::new()
Push-Location $root
try {
  $python = Join-Path $root '.venv\Scripts\python.exe'
  if (-not (Test-Path $python)) { $python = (Get-Command python -ErrorAction Stop).Source }
  & $python -m unittest discover -s scripts -p 'test_*.py'
  $results.Add([pscustomobject]@{Name='Python unit tests';Pass=($LASTEXITCODE -eq 0)})
  & $python scripts/validate_model.py
  $results.Add([pscustomobject]@{Name='ONNX validation';Pass=($LASTEXITCODE -eq 0)})
  if (Test-Path .\gradlew.bat) {
    $sdk = $env:ANDROID_SDK_ROOT; if (-not $sdk) { $sdk = $env:ANDROID_HOME }
    if (-not (Get-Command java -ErrorAction SilentlyContinue) -or -not $sdk -or -not (Test-Path $sdk)) {
      $results.Add([pscustomobject]@{Name='Android unit tests and lint';Pass=$false;Note='JDK or Android SDK is missing'})
      Write-Host 'BLOCKED: Android checks require JDK 17 and Android SDK.' -ForegroundColor Yellow
    } else {
      & .\gradlew.bat testDebugUnitTest lintDebug
      $gradleExit=$LASTEXITCODE
      $results.Add([pscustomobject]@{Name='Android unit tests and lint';Pass=($gradleExit -eq 0)})
    }
  } else { $results.Add([pscustomobject]@{Name='Android tests/lint';Pass=$false;Note='Gradle wrapper unavailable'}) }
} finally { Pop-Location }
$results | Format-Table -AutoSize
$failures = @($results | Where-Object { $_.Pass -eq $false })
if ($failures.Count -gt 0) { Write-Host "FAIL: $($failures.Count) required check(s) did not pass." -ForegroundColor Red; exit 1 }
Write-Host 'PASS: all configured checks passed.' -ForegroundColor Green
