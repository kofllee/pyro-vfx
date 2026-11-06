# Controlled gallery performance A/B without modifying the user's saved settings.
param(
    [ValidateSet('fast','fancy','fabulous')][string]$Graphics = 'fabulous',
    [int]$Width = 1280,
    [int]$Height = 720
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$options = Join-Path $root 'run/options.txt'
$original = [System.IO.File]::ReadAllBytes($options)
$running = @(Get-Process java,javaw -ErrorAction SilentlyContinue |
    Where-Object {$_.MainWindowTitle -match 'Minecraft'})
if ($running.Count -gt 0) { throw 'Close the existing Minecraft game before performance A/B; no settings changed.' }
$mode = switch ($Graphics) { fast {'0'} fancy {'1'} fabulous {'2'} }
$source = [System.IO.File]::ReadAllText($options)
if ($source -notmatch '(?m)^graphicsMode:[0-2](?=\r?$)') { throw 'graphicsMode setting missing; refusing to modify options.txt' }
$updated = [regex]::Replace($source, '(?m)^graphicsMode:[0-2](?=\r?$)', 'graphicsMode:' + $mode)
try {
    [System.IO.File]::WriteAllText($options, $updated)
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'run-client-bench-secondary.ps1') -Scenario 'gallery_fps_off,gallery_fps_all' -World 'Pyro VFX Museum' -Width $Width -Height $Height -Uncapped -RenderStateAudit
    if ($LASTEXITCODE -ne 0) { throw "Gallery FPS benchmark failed (exit $LASTEXITCODE)" }
} finally {
    [System.IO.File]::WriteAllBytes($options, $original)
    Write-Output 'Original Minecraft options.txt restored byte-for-byte.'
}
