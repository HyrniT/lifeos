# =============================================================================
#  End-to-end smoke test against the running service on :9080.
#
#  Everything goes through the public HTTP API — the same path the web app
#  takes: controller -> service -> Postgres, and for anything eventually
#  consistent, outbox relay -> in-process bus -> projector -> read model.
#
#  Usage:  powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
#
#  Section 9 deliberately fails logins until the throttle fires, which also
#  counts against this machine's IP budget (20 failures per 15 minutes). Three
#  consecutive runs are fine; beyond that, pass -SkipSecurityChecks or wait for
#  the window to roll over.
# =============================================================================
param(
  [string]$Base = "http://localhost:9080/api",
  [switch]$SkipSecurityChecks
)

$ErrorActionPreference = 'Stop'
$pass = 0
$fail = 0

function Check($name, $condition, $detail = "") {
  if ($condition) {
    Write-Output ("  PASS  {0}{1}" -f $name, $(if ($detail) { " -- $detail" } else { "" }))
    $script:pass++
  } else {
    Write-Output ("  FAIL  {0}{1}" -f $name, $(if ($detail) { " -- $detail" } else { "" }))
    $script:fail++
  }
}

function Api($method, $path, $body = $null, $token = $null) {
  $headers = @{ 'Content-Type' = 'application/json' }
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $args = @{ Uri = "$Base$path"; Method = $method; Headers = $headers; TimeoutSec = 25 }
  if ($body) { $args['Body'] = ($body | ConvertTo-Json -Depth 8 -Compress) }
  return Invoke-RestMethod @args
}

$stamp    = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$email    = "smoke+$stamp@lifeos.test"
$password = "SmokeTest12345"

Write-Output "== 1. Identity =============================================="
# UTC throughout: the deadline arithmetic in section 8 asserts on a reminder the
# scheduler computes in the user's own zone.
$reg = Api POST "/auth/register" @{ email = $email; password = $password; displayName = "Smoke Tester"; timezone = "UTC"; baseCurrency = "USD" }
Check "register returns an access token" ([bool]$reg.accessToken)
Check "register returns the profile" ($reg.user.email -eq $email) $reg.user.email

$login = Api POST "/auth/login" @{ email = $email; password = $password }
Check "login with the same credentials" ([bool]$login.accessToken)

$refreshed = Api POST "/auth/refresh" @{ refreshToken = $login.refreshToken }
Check "refresh rotates the token" ($refreshed.refreshToken -ne $login.refreshToken)

try {
  Api POST "/auth/refresh" @{ refreshToken = $login.refreshToken } | Out-Null
  Check "reused refresh token is rejected" $false "the burnt token was accepted"
} catch {
  Check "reused refresh token is rejected" $true "token-reuse detection fired"
}
$token = $refreshed.accessToken

try {
  Api GET "/users/me" | Out-Null
  Check "unauthenticated request is blocked" $false
} catch {
  Check "unauthenticated request is blocked" $true "401 without a bearer token"
}

$me = Api GET "/users/me" $null $token
Check "authenticated profile lookup" ($me.email -eq $email)

$sessions = Api GET "/users/me/sessions" $null $token
Check "the refresh token has a session behind it" (@($sessions).Count -ge 1) "$(@($sessions).Count) session(s)"

Write-Output ""
Write-Output "== 2. Habits (CQRS + event sourcing) ========================="
$habit = Api POST "/habits" @{ name = "Read 20 pages"; icon = "book-open"; frequency = "DAILY"; difficulty = "MEDIUM"; unit = "PAGES"; targetValue = 20 } $token
Check "habit created" ([bool]$habit.id) $habit.name

$checkin = Api POST "/habits/$($habit.id)/check-in" @{ value = 20; mood = 4 } $token
Check "check-in awards XP" ($checkin.xpAwarded -gt 0) "$($checkin.xpAwarded) XP"
Check "check-in sets the streak" ($checkin.currentStreak -ge 1) "streak $($checkin.currentStreak)"

$today = Api GET "/habits/today" $null $token
Check "today summary counts it as done" ($today.completed -ge 1) "$($today.completed)/$($today.totalDue)"

$stats = Api GET "/gamification/stats" $null $token
Check "gamification stats accumulate" ($stats.xp -gt 0) "level $($stats.level), $($stats.xp) XP"

$achievements = Api GET "/gamification/achievements" $null $token
$firstStep = $achievements | Where-Object { $_.code -eq 'FIRST_STEP' }
Check "FIRST_STEP achievement unlocked" ($firstStep.unlocked -eq $true)

$rebuild = Api POST "/habits/projections/rebuild" $null $token
Check "read model rebuilds from the event store" ($rebuild.replayedEvents -ge 2) "$($rebuild.replayedEvents) events replayed"

$afterRebuild = Api GET "/habits/$($habit.id)" $null $token
Check "streak survives the rebuild" ($afterRebuild.currentStreak -ge 1) "streak $($afterRebuild.currentStreak)"

$logs = Api GET "/habits/$($habit.id)/logs" $null $token
Check "check-in history readable" (@($logs).Count -ge 1) "$(@($logs).Count) log(s)"

Write-Output ""
Write-Output "== 3. Money =================================================="
Api POST "/accounts/seed-defaults?currency=USD" $null $token | Out-Null
$accounts = Api GET "/accounts" $null $token
Check "starter accounts created" (@($accounts).Count -ge 2) "$(@($accounts).Count) accounts"

$categories = Api GET "/categories" $null $token
Check "starter categories created" (@($categories).Count -ge 10) "$(@($categories).Count) categories"

$food = @($categories | Where-Object { $_.kind -eq 'EXPENSE' })[0]
$tx = Api POST "/expenses" @{ accountId = $accounts[0].id; categoryId = $food.id; amount = 42.50; type = "EXPENSE"; merchant = "Smoke Cafe" } $token
Check "transaction recorded" ($tx.amount -eq 42.50) "$($tx.amount) $($tx.currency) at $($tx.merchant)"

$accountsAfter = Api GET "/accounts" $null $token
$delta = $accounts[0].currentBalance - ($accountsAfter | Where-Object { $_.id -eq $accounts[0].id }).currentBalance
Check "account balance decreased by the amount" ([math]::Abs($delta - 42.50) -lt 0.001) "delta $delta"

$moneyStats = Api GET "/expenses/statistics" $null $token
Check "statistics report the spend" ($moneyStats.overview.totalExpense -ge 42.50) "total $($moneyStats.overview.totalExpense)"
Check "category breakdown attributes it" (@($moneyStats.byCategory).Count -ge 1) $moneyStats.byCategory[0].name
Check "insights engine produced output" (@($moneyStats.insights).Count -ge 1) $moneyStats.insights[0].title

Api POST "/budgets" @{ name = "Smoke budget"; categoryId = $food.id; amount = 100; period = "MONTHLY" } $token | Out-Null
$budgets = Api GET "/budgets" $null $token
Check "budget tracks live spend" ($budgets[0].spent -ge 42.50) "$($budgets[0].spent)/$($budgets[0].amount), state $($budgets[0].state)"
Check "safe daily spend computed" ($budgets[0].safeDailySpend -ge 0) "$($budgets[0].safeDailySpend)/day"

Write-Output ""
Write-Output "== 4. Planning ==============================================="
$task = Api POST "/tasks" @{ title = "Smoke task"; priority = "P1"; dueDate = (Get-Date).ToString('yyyy-MM-dd') } $token
Check "task created" ([bool]$task.id) "quadrant $($task.eisenhowerQuadrant)"

$agenda = Api GET "/tasks/agenda" $null $token
Check "task appears on today's agenda" (@($agenda.dueToday).Count -ge 1)

$project = Api POST "/projects" @{ name = "Smoke project"; icon = "folder"; status = "ACTIVE" } $token
Check "project created" ([bool]$project.id) $project.name

$linked = Api POST "/tasks" @{ title = "Task inside a project"; priority = "P3"; projectId = $project.id } $token
Check "task carries its project name" ($linked.projectName -eq "Smoke project") $linked.projectName

# What the project card's "n/m tasks done" link relies on: /planning?project=<id>
# passes the id straight to this filter rather than sieving the list in the browser.
#
# Assigned first and wrapped afterwards, never `@(Api GET ...)`: Invoke-RestMethod
# hands a JSON array to the pipeline as one object, so wrapping the call itself
# yields a single nested element and every .Count on it reads 1.
$scopedResult = Api GET "/tasks?projectId=$($project.id)" $null $token
$allResult = Api GET "/tasks" $null $token
$scoped = @($scopedResult)
$everything = @($allResult)
Check "tasks can be filtered to one project" ($scoped.Count -eq 1 -and $scoped[0].id -eq $linked.id -and $everything.Count -gt $scoped.Count) "$($scoped.Count) in the project, $($everything.Count) overall"

# The guard that stops a project disappearing out from under work in progress.
try {
  Api DELETE "/projects/$($project.id)" $null $token | Out-Null
  Check "project with open work cannot be deleted" $false "the delete went through"
} catch {
  Check "project with open work cannot be deleted" $true "409 while one task is still open"
}

Api POST "/tasks/$($linked.id)/status?status=DONE" $null $token | Out-Null
# Same reason as above — piping the call straight into Where-Object would filter
# the array as a single object, which matches whatever it is handed.
$projectsNow = Api GET "/projects" $null $token
$smokeProject = @($projectsNow | Where-Object { $_.id -eq $project.id })[0]
Check "project progress is counted from its tasks" ($smokeProject.progress -eq 1) "$($smokeProject.taskDone)/$($smokeProject.taskCount) done"
Api DELETE "/projects/$($project.id)" $null $token | Out-Null
$projectsAfter = Api GET "/projects" $null $token
Check "project deletes once nothing is open" (@($projectsAfter | Where-Object { $_.id -eq $project.id }).Count -eq 0)

Api POST "/tasks/$($task.id)/status?status=DONE" $null $token | Out-Null
$planStats = Api GET "/tasks/statistics?days=30" $null $token
Check "completion recorded in statistics" ($planStats.tasksDone -ge 1) "$($planStats.tasksDone) done"

$focus = Api POST "/focus/start" @{ type = "POMODORO"; plannedMinutes = 25; taskId = $task.id } $token
Check "focus session started" ([bool]$focus.id)
$ended = Api POST "/focus/$($focus.id)/end" @{ focusScore = 5; completed = $true } $token
Check "focus session ended" ($ended.endedAt -ne $null)

$goal = Api POST "/goals" @{ title = "Smoke goal"; targetValue = 10; currentValue = 3; unit = "steps"; targetDate = (Get-Date).AddDays(30).ToString('yyyy-MM-dd') } $token
Check "goal progress computed" ([math]::Abs($goal.progress - 0.3) -lt 0.01) "$([math]::Round($goal.progress * 100))% -- pace $($goal.pace)"

Api PUT "/journal" @{ mood = 4; energy = 4; highlights = "Ran the smoke test" } $token | Out-Null
$journal = Api GET "/journal" $null $token
Check "journal entry saved" (@($journal).Count -ge 1)

Write-Output ""
Write-Output "== 5. Analytics (outbox -> projector -> read model) ==========="
# The rollups are eventually consistent by design: the write commits, the outbox
# relay picks the row up on its next poll, and only then does the projector move
# a counter. Polling is the honest way to assert on that; a fixed sleep either
# flakes or hides how long the window really is.
$overview = $null
$waited = 0
do {
  Start-Sleep -Seconds 2
  $waited += 2
  $overview = Api GET "/analytics/overview" $null $token
  if ($overview.totalCheckIns -ge 1 -and $overview.totalSpent -ge 42.50) { break }
} while ($waited -lt 40)

Check "analytics overview served" ($overview -ne $null)
Check "rollup captured the check-in" ($overview.totalCheckIns -ge 1) "$($overview.totalCheckIns) check-in(s) projected in ${waited}s"
Check "rollup captured the spend" ($overview.totalSpent -ge 42.50) "$($overview.totalSpent) projected from the expense stream"
Check "rollup captured the completed task" ($overview.totalTasksCompleted -ge 1) "$($overview.totalTasksCompleted) task(s)"
Check "balance score computed" ($overview.balanceScore.habits -gt 0) "habits $($overview.balanceScore.habits)"

$activity = Api GET "/analytics/activity" $null $token
Check "event archive is being written" (@($activity).Count -ge 2) "$(@($activity).Count) events recorded"

Write-Output ""
Write-Output "== 6. Notifications =========================================="
$prefs = Api GET "/notifications/preferences" $null $token
Check "preferences created on first use" ($prefs -ne $null) "timezone $($prefs.timezone)"
Check "sensible default lead times" ($prefs.leadTimeMinutes -contains 1440 -and $prefs.leadTimeMinutes -contains 120) (($prefs.leadTimeMinutes -join ', ') + ' minutes')
Check "quiet hours on by default" ($prefs.quietHoursEnabled -eq $true) "$($prefs.quietFrom) to $($prefs.quietTo)"

$kinds = Api GET "/notifications/kinds" $null $token
Check "notification kinds exposed for the settings UI" (@($kinds).Count -ge 10) "$(@($kinds).Count) kinds"

$vapid = Api GET "/notifications/push/key" $null $token
Check "VAPID public key served for Web Push" ([bool]$vapid.publicKey) "$($vapid.publicKey.Substring(0, 16))... persistent=$($vapid.persistent)"

$test = Api POST "/notifications/test" $null $token
Check "test notification accepted" ($test.sent -eq $true)
$inbox = Api GET "/notifications" $null $token
# @() forces an array: PowerShell 5.1 unwraps a single-element pipeline result to
# a scalar, which has no .Count, so the assertion would silently compare $null.
Check "test notification lands in the durable inbox" (@($inbox | Where-Object { $_.kind -eq 'TEST' }).Count -ge 1)

$updated = Api PUT "/notifications/preferences" @{ leadTimeMinutes = @(1440, 120, 30); quietHoursEnabled = $false } $token
Check "preferences persist" ($updated.leadTimeMinutes -contains 30 -and $updated.quietHoursEnabled -eq $false)

Write-Output ""
Write-Output "== 7. Deadline reminders (the real scheduler) ================"
# A deadline 1h55m out means the "2 hours before" reminder is already due, so the
# next scan must produce it — and only it: the 1-day lead fired 22 hours ago,
# which is outside the catch-up window, and the 30-minute lead is still ahead.
$utcNow     = (Get-Date).ToUniversalTime()
$deadlineAt = $utcNow.AddMinutes(115)
$deadlineTask = Api POST "/tasks" @{
  title    = "Smoke deadline task"
  priority = "P1"
  dueDate  = $deadlineAt.ToString('yyyy-MM-dd')
  dueTime  = $deadlineAt.ToString('HH:mm:ss')
} $token
Check "task with a timed deadline created" ([bool]$deadlineTask.id) "due $($deadlineAt.ToString('HH:mm')) UTC"

$found = $null
$reminderWait = 0
do {
  Start-Sleep -Seconds 5
  $reminderWait += 5
  $all = Api GET "/notifications" $null $token
  $found = @($all | Where-Object { $_.kind -eq 'TASK_DUE_SOON' }) | Select-Object -First 1
} while (-not $found -and $reminderWait -lt 75)

Check "scheduler produced a before-deadline reminder" ([bool]$found) $(if ($found) { "`"$($found.title)`" after ${reminderWait}s" } else { "nothing after ${reminderWait}s -- is run-local.ps1 setting LIFEOS_REMINDERS_INTERVAL_MS?" })
if ($found) {
  Check "reminder deep-links to the task" ($found.deepLink -like "*$($deadlineTask.id)*") $found.deepLink
  Check "reminder names the lead time" ($found.title -match 'hour|day|minute') $found.title
}

# Idempotency: the scheduler re-emits on every tick, so a second copy would mean
# the dedupe key is not doing its job.
Start-Sleep -Seconds 20
$again = Api GET "/notifications" $null $token
$dueSoonCount = @($again | Where-Object { $_.kind -eq 'TASK_DUE_SOON' }).Count
Check "repeated scans do not duplicate the reminder" ($dueSoonCount -eq 1) "$dueSoonCount TASK_DUE_SOON notification(s) across several scans"

$unread = Api GET "/notifications/unread-count" $null $token
Check "unread count reported" ($unread.unread -ge 1) "$($unread.unread) unread"
Api POST "/notifications/read-all" $null $token | Out-Null
$afterRead = Api GET "/notifications/unread-count" $null $token
Check "mark-all-read clears the badge" ($afterRead.unread -eq 0)

Write-Output ""
Write-Output "== 8. Administration ========================================="
$admin = Api POST "/auth/login" @{ email = "admin"; password = "admin" }
Check "seeded admin signs in with admin/admin" ([bool]$admin.accessToken)
Check "admin holds the ADMIN role" ($admin.user.roles -contains 'ADMIN') ($admin.user.roles -join ',')
$adminToken = $admin.accessToken

$adminOverview = Api GET "/admin/overview" $null $adminToken
Check "admin overview reachable" ($adminOverview.totalUsers -ge 2) "$($adminOverview.totalUsers) users"
Check "audit trail is being written" (@($adminOverview.auditBreakdown).Count -ge 1) "$(@($adminOverview.auditBreakdown).Count) action types"

try {
  Api GET "/admin/overview" $null $token | Out-Null
  Check "non-admin blocked from /api/admin" $false "a normal user got in"
} catch {
  Check "non-admin blocked from /api/admin" $true "403 for a non-admin bearer token"
}

$modules = Api GET "/admin/system/services" $null $adminToken
Check "module inventory visible to admin" (@($modules).Count -ge 6) "$(@($modules).Count) modules"

$health = Api GET "/admin/system/health" $null $adminToken
Check "infrastructure probes report UP" ($health.postgres.status -eq 'UP' -and $health.application.status -eq 'UP') "postgres $($health.postgres.status) in $($health.postgres.latencyMs)ms"

$users = Api GET "/admin/users?q=smoke&size=5" $null $adminToken
Check "admin can find the account this run created" ($users.content.Count -ge 1) "$($users.totalElements) match(es) for 'smoke'"

Write-Output ""
Write-Output "== 9. Security controls ======================================"
if ($SkipSecurityChecks) {
  Write-Output "  SKIP  brute-force throttle (-SkipSecurityChecks)"
} else {
  # Five failures lock the account, so this normally stops on the sixth attempt
  # and costs the shared per-IP budget six of its twenty failures.
  $blocked = $false
  for ($i = 0; $i -lt 10; $i++) {
    try { Api POST "/auth/login" @{ email = $email; password = "wrong-password-$i" } | Out-Null }
    catch {
      if ($_.Exception.Response.StatusCode.value__ -eq 429) { $blocked = $true; break }
    }
  }
  Check "brute force is throttled" $blocked "429 after $($i + 1) failed attempts"
}

Write-Output ""
Write-Output "=============================================================="
Write-Output ("RESULT: {0} passed, {1} failed" -f $pass, $fail)
if ($fail -gt 0) { exit 1 }
