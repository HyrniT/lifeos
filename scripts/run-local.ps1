# =============================================================================
#  Starts LifeOS natively on Windows against the Docker Postgres.
#
#  One backend process and one Vite dev server — that is the whole system in
#  this edition. Both write to logs\ and neither holds a console window, so the
#  script returns as soon as the API answers its health probe.
#
#  Usage:  powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
#  Stop :  powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
#
#  Infrastructure first (Docker lives in WSL on this machine):
#      wsl -d Ubuntu -u root bash -lc 'cd /mnt/c/Users/nuan/dev/lifeos-mono && bash scripts/infra-up.sh'
# =============================================================================
param(
  # `java` on PATH is Zulu 8 here, which cannot run a Boot 3 / Java 21 jar.
  [string]$JavaHome     = "C:\Users\nuan\tools\jdk-21.0.12+8",
  [string]$Root         = (Split-Path -Parent $PSScriptRoot),
  # Must match POSTGRES_PORT in .env — 55432, not 5432, so the Postgres service
  # this machine already runs is never the one we connect to by accident.
  [int]   $PostgresPort = 55432,
  [int]   $Port         = 9080,
  [switch]$Build,
  [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$env:Path      = "$JavaHome\bin;$env:Path"

$logDir = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if ($Build) {
  Write-Output "==> mvn package (skipping tests)"
  & mvn -f (Join-Path $Root "backend\pom.xml") package -DskipTests -q
  if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
}

$jar = Join-Path $Root "backend\target\lifeos.jar"
if (-not (Test-Path $jar)) {
  throw "No jar at $jar. Re-run with -Build, or: mvn -f backend/pom.xml package -DskipTests"
}

# -----------------------------------------------------------------------------
#  Backend
# -----------------------------------------------------------------------------
# The scheduler intervals are the only ones that differ from the defaults in
# application.yml: five minutes is right for production and unusable for a smoke
# test, where a reminder has to be observable inside a minute.
$backendEnv = @{
  DATABASE_URL                             = "jdbc:postgresql://localhost:$PostgresPort/lifeos"
  DATABASE_USERNAME                        = "lifeos"
  DATABASE_PASSWORD                        = "lifeos_secret"
  # Must be >= 64 bytes for HS512.
  JWT_SECRET                               = "local-development-secret-please-change-in-production-0123456789abcdefghij"
  ADMIN_SEED_ENABLED                       = "true"
  ADMIN_USERNAME                           = "admin"
  ADMIN_PASSWORD                           = "admin"
  LIFEOS_CORS_ORIGINS                      = "http://localhost:5273,http://localhost:4273"
  PORT                                     = "$Port"

  LIFEOS_OUTBOX_POLL_MS                    = "1000"
  LIFEOS_REMINDERS_INTERVAL_MS             = "15000"
  LIFEOS_REMINDERS_HABIT_INTERVAL_MS       = "15000"
  LIFEOS_REMINDERS_GOAL_INTERVAL_MS        = "20000"
  LIFEOS_NOTIFICATIONS_DISPATCH_MS         = "15000"
  LIFEOS_NOTIFICATIONS_SUMMARY_INTERVAL_MS = "30000"
}

$assignments = @()
foreach ($key in $backendEnv.Keys) { $assignments += "`$env:$key='$($backendEnv[$key])'" }
$assignments += "`$env:JAVA_HOME='$JavaHome'"
$assignments += "`$env:Path='$JavaHome\bin;' + `$env:Path"

$backendLog = Join-Path $logDir "backend.log"
$command    = ($assignments -join '; ') + "; java -jar '$jar' *> '$backendLog'"

Start-Process -FilePath "powershell.exe" `
              -ArgumentList "-NoProfile", "-WindowStyle", "Hidden", "-Command", $command `
              -WindowStyle Hidden
Write-Output "STARTED backend on :$Port  -> logs\backend.log"

# -----------------------------------------------------------------------------
#  Web app
# -----------------------------------------------------------------------------
if (-not $SkipFrontend) {
  $frontend = Join-Path $Root "frontend"
  if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Write-Output "==> npm install (first run)"
    Push-Location $frontend
    try { & npm install --silent } finally { Pop-Location }
  }

  $frontendLog = Join-Path $logDir "frontend.log"
  # VITE_PROXY_TARGET so a non-default -Port still reaches the backend: the proxy
  # in vite.config.ts falls back to 9080 when it is unset.
  $frontendCommand = "Set-Location '$frontend'; `$env:VITE_PROXY_TARGET='http://localhost:$Port'; npm run dev *> '$frontendLog'"

  Start-Process -FilePath "powershell.exe" `
                -ArgumentList "-NoProfile", "-WindowStyle", "Hidden", "-Command", $frontendCommand `
                -WindowStyle Hidden
  Write-Output "STARTED web app on :5273  -> logs\frontend.log"
}

# -----------------------------------------------------------------------------
#  Wait for the API rather than sleeping a fixed amount: Flyway migrates six
#  schemas on a cold database and that is much slower than a warm restart.
# -----------------------------------------------------------------------------
Write-Output ""
Write-Output "==> waiting for the API on :$Port (up to 120s)"
$up = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 2
  try {
    $health = Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -TimeoutSec 5
    if ($health.status -eq 'UP') { $up = $true; break }
  } catch {
    # Connection refused until the context is up; nothing to report yet.
  }
}

if (-not $up) {
  Write-Output "The API did not report UP within 120s. Last lines of logs\backend.log:"
  if (Test-Path $backendLog) { Get-Content $backendLog -Tail 25 }
  exit 1
}

Write-Output "API is UP after $($i * 2)s."
Write-Output ""
Write-Output "  http://localhost:5273              Web app (admin / admin)"
Write-Output "  http://localhost:5273/admin/       Admin console"
Write-Output "  http://localhost:$Port/swagger-ui.html   API docs"
Write-Output ""
Write-Output "Smoke test:  powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1"
