# =============================================================================
#  Stops the locally-running backend and web app. The Postgres container is left
#  alone unless -Infra is given, because losing it means Flyway migrating six
#  schemas again on the next start.
#
#  Usage:  powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
#          powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1 -Infra
# =============================================================================
param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot),
  [switch]$Infra
)

$stopped = 0

# Two passes, because neither alone is reliable.
#
# The command line is the precise signal — it catches a crashed-but-alive JVM
# that never bound 9080, which the next start would otherwise collide with. But
# Win32_Process only reveals CommandLine to a session with rights over the target:
# start the app from one shell and run this from another, and every CommandLine
# comes back empty, the filter matches nothing, and the script cheerfully reports
# "Stopped 0" while everything keeps running. The port pass covers that, and also
# catches vite, which npm spawns as a grandchild whose command line names the vite
# binary rather than this repository.
Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'node.exe'" | ForEach-Object {
  if ($_.CommandLine -and $_.CommandLine -match 'lifeos-mono\\(backend|frontend)') {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    $script:stopped++
  }
}

# 9080 the service, 5273 the web app, 5274 the admin console's own dev server.
foreach ($port in 9080, 5273, 5274) {
  $owners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
  foreach ($owner in $owners) {
    if ($owner.OwningProcess -and $owner.OwningProcess -ne 0 -and $owner.OwningProcess -ne $PID) {
      Stop-Process -Id $owner.OwningProcess -Force -ErrorAction SilentlyContinue
      $script:stopped++
    }
  }
}

Write-Output "Stopped $stopped LifeOS process(es) (backend + web dev server)."

if ($Infra) {
  # C:\Users\nuan\dev\lifeos-mono -> /mnt/c/Users/nuan/dev/lifeos-mono
  $wslRoot = "/mnt/" + $Root.Substring(0, 1).ToLower() + ($Root.Substring(2) -replace '\\', '/')
  Write-Output "Stopping Postgres..."
  wsl -d Ubuntu -u root bash -lc "cd '$wslRoot' && docker compose -p lifeos-mono -f docker-compose.yml -f scripts/compose-local.yml down"
} else {
  Write-Output "Postgres is still running. To stop it too, re-run with -Infra."
}
