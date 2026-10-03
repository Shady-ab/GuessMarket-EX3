$ErrorActionPreference = 'Stop'

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = [string](Join-Path $dir 'readme.html')
$docx = [string](Join-Path $dir 'readme.docx')
$pdf = [string](Join-Path $dir 'readme.pdf')

if (-not (Test-Path $src)) {
    Write-Host 'readme.html not found.'
    exit 1
}

try {
    $word = New-Object -ComObject Word.Application
} catch {
    Write-Host 'Microsoft Word is not available - cannot create readme.docx / readme.pdf.'
    exit 2
}

$word.Visible = $false
$word.DisplayAlerts = 0
$doc = $null
try {
    $doc = $word.Documents.Open($src, $false, $true)
    # 3 = wdPrintView, otherwise the docx opens in web layout
    $doc.ActiveWindow.View.Type = 3
    $doc.PageSetup.TopMargin = 54
    $doc.PageSetup.BottomMargin = 54
    $doc.PageSetup.LeftMargin = 54
    $doc.PageSetup.RightMargin = 54
    # 16 = wdFormatDocumentDefault (docx), 17 = wdExportFormatPDF
    $doc.SaveAs2($docx, 16)
    $doc.ExportAsFixedFormat($pdf, 17)
    Write-Host 'Created readme.docx and readme.pdf'
} catch {
    Write-Host "README conversion failed: $($_.Exception.Message)"
    exit 3
} finally {
    if ($doc) { $doc.Close(0) }
    $word.Quit()
}
