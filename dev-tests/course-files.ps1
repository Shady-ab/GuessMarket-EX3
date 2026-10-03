# Loads the course sample files into a freshly deployed server and checks the results.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/GuessMarket'
$dir = Join-Path (Split-Path -Parent $PSScriptRoot) 'samples\course'
$curl = 'curl.exe'
$jar = Join-Path $env:TEMP 'gm-course-cookies.txt'
$fails = 0

function Check($label, $ok, $detail) {
    if ($ok) { Write-Host "PASS  $label" -ForegroundColor Green }
    else { Write-Host "FAIL  $label  $detail" -ForegroundColor Red; $script:fails++ }
}
function Post($path, $form) {
    $args = @('-s', '-w', "`n%{http_code}", '-b', $jar, '-c', $jar)
    foreach ($k in $form.Keys) { $args += @('--data-urlencode', "$k=$($form[$k])") }
    $out = & $curl @args "$base$path"
    $lines = $out -split "`n"; return @{ status = [int]$lines[-1]; body = ($lines[0..($lines.Count - 2)] -join "`n") }
}
function Get($path) {
    $out = & $curl -s -w "`n%{http_code}" -b $jar -c $jar "$base$path"
    $lines = $out -split "`n"; return @{ status = [int]$lines[-1]; body = ($lines[0..($lines.Count - 2)] -join "`n") }
}
function Upload($file) {
    $out = & $curl -s -w "`n%{http_code}" -b $jar -c $jar -F "file=@$file;type=application/xml" "$base/upload"
    $lines = $out -split "`n"; return @{ status = [int]$lines[-1]; body = ($lines[0..($lines.Count - 2)] -join "`n") }
}

$schemas = New-Object System.Xml.Schema.XmlSchemaSet
$schemas.Add('', (Join-Path $dir 'GM-EX3-Schema.xsd')) | Out-Null
foreach ($f in 'ex3-course-three-events.xml', 'ex3-course-mujtaba.xml') {
    $doc = New-Object System.Xml.XmlDocument
    $doc.Schemas = $schemas
    $doc.Load((Join-Path $dir $f))
    $errors = @()
    $doc.Validate({ param($s, $e) $script:errors += $e.Message })
    Check "$f matches the course schema" ($errors.Count -eq 0) ($errors -join '; ')
}

if (Test-Path $jar) { Remove-Item $jar }
$r = Post '/login' @{ username = 'Teacher' }
Check 'login Teacher' ($r.status -eq 200) $r.body

$r = Upload (Join-Path $dir 'ex3-course-three-events.xml')
Check 'three-events file is accepted' ($r.status -eq 200) $r.body
Write-Host "      $($r.body)"
$r = Upload (Join-Path $dir 'ex3-course-mujtaba.xml')
Check 'mujtaba file is accepted (events accumulate)' ($r.status -eq 200) $r.body
Write-Host "      $($r.body)"
$r = Upload (Join-Path $dir 'ex3-course-three-events.xml')
Check 'same file again is rejected (names exist)' ($r.status -eq 400) $r.body
Write-Host "      $($r.body)"

$events = (Get '/events').body | ConvertFrom-Json
Check 'server has exactly 4 events' ($events.Count -eq 4) ($events.Count)
Check 'Teacher is MM of all events' (@($events | Where-Object { $_.marketMaker -ne 'Teacher' }).Count -eq 0) ''

$r = Post '/deposit' @{ amount = 2000 }
Check 'deposit 2000' ($r.status -eq 200) $r.body
foreach ($e in $events) {
    $r = Post '/event/open' @{ id = $e.id }
    Check "open '$($e.name)'" ($r.status -eq 200) $r.body
}
$me = (Get '/me?ledgerFrom=0').body | ConvertFrom-Json
foreach ($line in $me.ledger) { Write-Host ("      {0,10:N2}  {1,10:N2}  {2}" -f $line.amount, $line.balanceAfter, $line.description) }
Check 'balance after opening = 2000 - 1000 - 100 - 138.63 - 69.31' ([math]::Abs($me.balance - 692.06) -lt 0.011) $me.balance

$r = Post '/logout' @{}
Write-Host ''
if ($fails -eq 0) { Write-Host 'ALL COURSE FILE CHECKS PASSED' -ForegroundColor Green } else { Write-Host "$fails FAILED" -ForegroundColor Red }
