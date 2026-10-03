# End-to-end API test against a running Tomcat (http://localhost:8080/GuessMarket).
# Usage: powershell -File dev-tests\api-smoke.ps1
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/GuessMarket'
$samples = Join-Path (Split-Path -Parent $PSScriptRoot) 'samples'
$suffix = Get-Random -Maximum 100000

function Call($session, $method, $path, $body) {
    try {
        if ($method -eq 'GET') {
            $r = Invoke-WebRequest -Uri "$base$path" -WebSession $session -UseBasicParsing
        } else {
            $r = Invoke-WebRequest -Uri "$base$path" -Method Post -Body $body -WebSession $session -UseBasicParsing
        }
        return @{ status = [int]$r.StatusCode; json = ($r.Content | ConvertFrom-Json) }
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        return @{ status = [int]$resp.StatusCode; json = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Upload($session, $file) {
    $curl = "curl.exe"
    $cookie = ($session.Cookies.GetCookies($base) | Where-Object Name -eq 'JSESSIONID').Value
    $out = & $curl -s -w "`n%{http_code}" -b "JSESSIONID=$cookie" -F "file=@$file;type=application/xml" "$base/upload"
    $lines = $out -split "`n"
    return @{ status = [int]$lines[-1]; json = (($lines[0..($lines.Length - 2)] -join "`n") | ConvertFrom-Json) }
}

$pass = 0; $fail = 0
function Check($label, $condition, $detail) {
    if ($condition) { $script:pass++; Write-Host "PASS  $label" -ForegroundColor Green }
    else { $script:fail++; Write-Host "FAIL  $label  -> $detail" -ForegroundColor Red }
}

$a = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$b = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$c = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$alice = "Alice$suffix"; $bob = "Bob$suffix"

$r = Call $a GET '/events' $null
Check 'events without login is 401' ($r.status -eq 401) $r.status

$r = Call $a POST '/login' @{ username = $alice }
Check 'alice logs in' ($r.status -eq 200) $r.json.error
$r = Call $b POST '/login' @{ username = $alice }
Check 'duplicate online name rejected (409)' ($r.status -eq 409) "$($r.status) $($r.json.error)"
$r = Call $b POST '/login' @{ username = $bob }
Check 'bob logs in' ($r.status -eq 200) $r.json.error

$r = Upload $a (Join-Path $samples 'ex3-error-commission.xml')
Check 'bad commission rejected' ($r.status -eq 400 -and $r.json.error -match 'ommission') $r.json.error
$r = Upload $a (Join-Path $samples 'ex3-error-duplicate-name.xml')
Check 'duplicate name in file rejected' ($r.status -eq 400 -and $r.json.error -match 'Duplicate') $r.json.error
$r = Upload $a (Join-Path $samples 'ex3-error-one-option.xml')
Check 'single option rejected' ($r.status -eq 400) $r.json.error

$events = (Call $a GET '/events' $null).json
$already = @($events | Where-Object name -eq 'Will it rain tomorrow ?').Count -gt 0
if (-not $already) {
    $r = Upload $a (Join-Path $samples 'ex3-valid-binary.xml')
    Check 'valid binary file loaded' ($r.status -eq 200) $r.json.error
    $r = Upload $b (Join-Path $samples 'ex3-valid-multi-option.xml')
    Check 'valid multi-option file loaded by bob' ($r.status -eq 200) $r.json.error
}
$r = Upload $b (Join-Path $samples 'ex3-error-existing-name.xml')
Check 'event name that already exists rejected' ($r.status -eq 400 -and $r.json.error -match 'already exists') $r.json.error

$events = (Call $a GET '/events' $null).json
Check 'events accumulate (>= 6)' ($events.Count -ge 6) $events.Count
$rain = $events | Where-Object name -eq 'Will it rain tomorrow ?'
$cup = $events | Where-Object name -eq 'World Cup 2026 Champion'
$weather = $events | Where-Object name -eq 'Tel Aviv Weather Saturday'
$book = $events | Where-Object name -eq 'World Cap Winner'

if ($rain.marketMaker -ne $alice) {
    Write-Host "Events were uploaded by an earlier run; MM-only checks use their market makers." -ForegroundColor Yellow
    Write-Host "Restart Tomcat for a full run."
    Write-Host "PASSED $pass, FAILED $fail"
    exit 0
}

$r = Call $a POST '/event/open' @{ id = $rain.id }
Check 'open without money fails' ($r.status -eq 400 -and $r.json.error -match 'enough cash') $r.json.error
$r = Call $a POST '/deposit' @{ amount = 'abc' }
Check 'deposit non-number rejected' ($r.status -eq 400) $r.json.error
$r = Call $a POST '/deposit' @{ amount = 1000 }
Check 'alice deposits 1000' ($r.status -eq 200) $r.json.error
$r = Call $b POST '/event/open' @{ id = $rain.id }
Check 'non-MM cannot open' ($r.status -eq 400 -and $r.json.error -match 'Only market maker') $r.json.error
$r = Call $a POST '/event/open' @{ id = $rain.id }
Check 'alice opens LMSR event' ($r.status -eq 200) $r.json.error

$r = Call $b POST '/event/buy' @{ id = $rain.id; option = 1; quantity = 100 }
Check 'bob buys with 0 cash -> goes negative but succeeds' ($r.status -eq 200) $r.json.error
$me = (Call $b GET '/me?ledgerFrom=0' $null).json
Check 'bob is blocked after negative balance' ($me.blocked -eq $true -and $me.balance -lt 0) "$($me.balance) $($me.blocked)"
$r = Call $b POST '/event/buy' @{ id = $rain.id; option = 1; quantity = 1 }
Check 'blocked bob cannot buy' ($r.status -eq 400 -and $r.json.error -match 'blocked') $r.json.error
$r = Call $b POST '/deposit' @{ amount = 500 }
$me = (Call $b GET '/me?ledgerFrom=0' $null).json
Check 'deposit unblocks bob' ($me.blocked -eq $false) $me.balance
Check 'bob ledger has 2 lines' ($me.ledger.Count -eq 2 -and $me.ledgerSize -eq 2) $me.ledger.Count
$delta = (Call $b GET '/me?ledgerFrom=2' $null).json
Check 'ledger delta returns nothing new' ($delta.ledger.Count -eq 0 -and $delta.ledgerSize -eq 2) $delta.ledger.Count

$det = (Call $a GET "/event?id=$($rain.id)" $null).json
Check 'LMSR price after 100 YES with b=200 is ~0.62' ([math]::Abs($det.options[0].price - 0.62) -lt 0.01) $det.options[0].price

# Multi-option LMSR (bob is MM)
$r = Call $b POST '/event/open' @{ id = $cup.id }
Check 'bob opens 4-option LMSR (subsidy 100*ln4=138.63)' ($r.status -eq 200) $r.json.error
$r = Call $a POST '/event/buy' @{ id = $cup.id; option = 3; quantity = 50 }
Check 'alice buys option 3 of 4' ($r.status -eq 200) $r.json.error
$r = Call $a POST '/event/buy' @{ id = $cup.id; option = 5; quantity = 1 }
Check 'option 5 of 4 rejected' ($r.status -eq 400) $r.json.error
$det = (Call $a GET "/event?id=$($cup.id)" $null).json
$sum = ($det.options | Measure-Object -Property price -Sum).Sum
Check 'four LMSR prices sum to 1' ([math]::Abs($sum - 1) -lt 0.02) $sum

# Multi-option order book with mint (bob is MM, d=3, 3 options)
$r = Call $b POST '/event/open' @{ id = $weather.id }
Check 'bob opens 3-option order book' ($r.status -eq 200) $r.json.error
$r = Call $a POST '/event/order' @{ id = $weather.id; option = 1; side = 'BUY'; quantity = 5; price = 1.0 }
$r = Call $b POST '/event/order' @{ id = $weather.id; option = 2; side = 'BUY'; quantity = 5; price = 1.0 }
$r = Call $a POST '/event/order' @{ id = $weather.id; option = 3; side = 'BUY'; quantity = 5; price = 1.5 }
Check 'third bid completes a 3-option mint' ($r.status -eq 200 -and $r.json.message -match 'Minted 5') $r.json.message
$r = Call $b POST '/event/order' @{ id = $weather.id; option = 1; side = 'SELL'; quantity = 50; price = 0.5 }
Check 'MM sells initial shares (rests)' ($r.status -eq 200) $r.json.error
$r = Call $a POST '/event/order' @{ id = $weather.id; option = 1; side = 'BUY'; quantity = 10; price = 0.6 }
Check 'alice buy matches resting ask' ($r.status -eq 200 -and $r.json.message -match 'Filled: 10') $r.json.message
$r = Call $a POST '/event/order' @{ id = $weather.id; option = 1; side = 'BUY'; quantity = 1; price = 3 }
Check 'price >= d rejected' ($r.status -eq 400) $r.json.error

$r = Call $b POST '/event/close' @{ id = $weather.id; winner = 1 }
Check 'bob closes order book, option 1 wins' ($r.status -eq 200) $r.json.error
$me = (Call $a GET '/me?ledgerFrom=0' $null).json
$h = $me.events | Where-Object eventName -eq 'Tel Aviv Weather Saturday'
Check 'alice involvement shows winner and profit' ($h.winner -eq 'Sunny' -and $null -ne $h.profit) "$($h.winner) $($h.profit)"
Check 'alice ledger has payout line' (@($me.ledger | Where-Object description -match 'Payout').Count -ge 1) ''

$users = (Call $c GET '/users' $null)
Check 'users needs login' ($users.status -eq 401) $users.status
$users = (Call $a GET '/users' $null).json
$bobRow = $users | Where-Object name -eq $bob
Check 'users list shows bob as MM and online' ($bobRow.marketMaker -eq $true -and $bobRow.online -eq $true) ($bobRow | ConvertTo-Json -Compress)

# Chat bonus
$r = Call $a POST '/chat' @{ message = 'hello from alice' }
$chat = (Call $b GET '/chat?version=0' $null).json
Check 'bob sees alice chat line' (@($chat.entries | Where-Object text -eq 'hello from alice').Count -eq 1) ($chat | ConvertTo-Json -Compress)
$chat2 = (Call $b GET "/chat?version=$($chat.version)" $null).json
Check 'chat delta is empty' ($chat2.entries.Count -eq 0) $chat2.entries.Count

# Logout frees the name; re-login resumes the same account
$r = Call $a POST '/logout' @{}
$r = Call $c POST '/login' @{ username = $alice }
Check 'after logout the name can log in again' ($r.status -eq 200) $r.json.error
$me = (Call $c GET '/me?ledgerFrom=0' $null).json
Check 're-login resumes the same account' ($me.ledgerSize -gt 3) $me.ledgerSize

Write-Host ""
Write-Host "PASSED $pass, FAILED $fail"
if ($fail -gt 0) { exit 1 }
