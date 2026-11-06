# Run renderer profiles one at a time, preserving the user's mods/config.
# Requires a second monitor, same as run-client-bench-secondary.ps1.
# No profiles are left installed after a successful or failed run.
param(
    [string]$World = 'Pyro VFX Museum',
    [string]$Scenarios = 'museum_gpu_sprite,museum_model_custom,museum_sprite_additive,museum_model_translucent',
    [string]$Profiles = 'sodium,embeddium,iris,iris-complementary,iris-bsl,iris-photon'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
$archive = Join-Path $root 'run/pyrovfx-benchmarks/museum-native-compatibility'
New-Item -ItemType Directory -Force -Path $archive | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($selection in ($Profiles -split ',')) {
        $selection = $selection.Trim()
        $fields = $selection -split '-',2
        $profile = $fields[0]
        $shader = if ($fields.Length -gt 1) {$fields[1]} else {'none'}
        $start = Get-Date
        $status = 'not-started'
        $errorMessage = ''
        $installed = $false
        try {
            & python tools/museum_compat_profile.py setup --profile $profile --shader $shader
            if ($LASTEXITCODE -ne 0) { throw "Setup returned exit $LASTEXITCODE" }
            $installed = $true
            $benchArgs = @('-World', $World, '-Scenario', $Scenarios,
                '-Width', '1280', '-Height', '720', '-Uncapped', '-RenderStateAudit', '-GpuTelemetry')
            & powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-client-bench-secondary.ps1 @benchArgs
            if ($LASTEXITCODE -ne 0) { throw "Minecraft run returned exit $LASTEXITCODE" }
            $status = 'completed'
        } catch {
            $status = 'failed'
            $errorMessage = $_.Exception.Message
            Write-Warning "$selection : $errorMessage"
        } finally {
            if ($installed) {
                & python tools/museum_compat_profile.py restore
                if ($LASTEXITCODE -ne 0) {throw "Could not safely restore mods/config for $selection"}
            }
        }
        $dir = Join-Path $archive $selection
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $expected = ($Scenarios -split ',').Count
        $copied = 0
        foreach ($scenario in $Scenarios -split ',') {
            $matching = @(Get-ChildItem 'run/pyrovfx-benchmarks' -Filter "$scenario-*.json" |
                Where-Object {$_.LastWriteTime -ge $start} | Sort-Object LastWriteTime -Descending)
            if ($matching.Count -gt 0) {
                Copy-Item $matching[0].FullName -Destination (Join-Path $dir $matching[0].Name)
                $copied++
                $report = Get-Content $matching[0].FullName -Raw | ConvertFrom-Json
                if ($report.screenshot -and (Test-Path $report.screenshot)) {
                    Copy-Item $report.screenshot -Destination (Join-Path $dir ($scenario + '.png'))
                }
                if ($UnsafeShaderGpuBenchmark -and $shader -ne 'none' -and
                        ($report.status -ne 'completed' -or
                        $report.selectedRenderer -notmatch '^GPU ')) {
                    $status = 'incomplete'
                    $errorMessage += "$scenario did not prove GPU rendering: $($report.selectedRenderer). "
                }
            }
        }
        if ($status -eq 'completed' -and $copied -lt $expected) {
            $status = 'incomplete'
            $errorMessage = "Only $copied of $expected benchmark reports generated"
        }
        $results.Add([pscustomobject]@{
            profile=$selection;status=$status;reports=$copied;expected=$expected;
            message=$errorMessage;date=(Get-Date).ToString('o')
        })
        $results | ConvertTo-Json |Set-Content -Encoding utf8 (Join-Path $archive 'profile-results.json')
    }
} finally {
    Pop-Location
}
$results | Format-Table -AutoSize
if (@($results | Where-Object {$_.status -ne 'completed'}).Count -gt 0) { exit 1 }
