# Windows: run a real Minecraft benchmark without taking over the primary screen.
# Use this launcher for interactive/developer benchmarks, NOT Gradle runClientBench directly.
param(
    [string]$Scenario = 'analytic_uv_1k',
    [int]$Width = 1280,
    [int]$Height = 720,
    [switch]$Uncapped,
    [switch]$DisableCollisionCache,
    [switch]$RequireGpuAnalytic,
    [switch]$ForceCpuAnalytic,
    [switch]$ForceLegacyCpu,
    [switch]$ForceLegacyRender,
    [switch]$DisableCulling,
    [ValidateRange(0,64)][int]$TinyAnalyticThreshold = 12,
    [switch]$RequireGpuInstancing,
    [switch]$ForceLegacyModels,
    [switch]$Heavy,
    [switch]$GlobalStress,
    [switch]$GpuTelemetry,
    [switch]$ProbeVram,
    [int]$SoakTicks = 600,
    [switch]$AdaptiveSpawns,
    [double]$AdaptiveCpuTargetMs = 0,
    [switch]$RenderStateAudit,
    [switch]$MuseumBuild,
    [string]$World = 'Test VFX'
)

$ErrorActionPreference = 'Stop'
# Reject misspelled scenarios before opening a Minecraft window. Otherwise
# the auto-run harness remains idle indefinitely waiting for an unknown test.
$scenarioSource = Get-Content (Join-Path $PSScriptRoot '../src/development/java/net/kofllee/pyrovfx/client/dev/VfxBenchScenarios.java') -Raw
$museumManifest = Join-Path $PSScriptRoot '../src/development/resources/pyro_vfx_museum_exhibits.tsv'
$museumIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
Get-Content $museumManifest | Where-Object { $_ -and -not $_.StartsWith('#') } |
    ForEach-Object { [void]$museumIds.Add(($_ -split '\|',2)[0]) }
$scenarioNames = @($Scenario -split ',' | ForEach-Object { $_.Trim() })
foreach ($scenarioName in $scenarioNames) {
    if ([string]::IsNullOrWhiteSpace($scenarioName) -or
            ($scenarioName -like 'museum_*' -and
             -not $museumIds.Contains($scenarioName.Substring(7))) -or
            ($scenarioName -notlike 'museum_*' -and
             -not $scenarioSource.Contains('"' + $scenarioName + '"'))) {
        throw "Unknown benchmark scenario '$scenarioName'; check VfxBenchScenarios before opening Minecraft."
    }
}
Add-Type -AssemblyName System.Windows.Forms
$secondary = @([System.Windows.Forms.Screen]::AllScreens | Where-Object { -not $_.Primary })
if ($secondary.Count -lt 1) { throw 'No secondary monitor; benchmark aborted to protect the active desktop.' }
if ($Width -gt $secondary[0].Bounds.Width -or $Height -gt $secondary[0].Bounds.Height) {
    throw "Benchmark window ${Width}x${Height} does not fit secondary display $($secondary[0].Bounds)."
}

# The benchmark must NEVER move, activate or change the z-order of unrelated
# windows (notably Chrome tabs whose title may contain "Minecraft").
# Identify only a Java-owned GLFW window; never restore focus across virtual
# desktops, since that can activate a browser on a different Windows desktop.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
public static class PyroBenchDesktop {
    public delegate bool EnumWindowsProc(IntPtr hwnd, IntPtr param);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc cb, IntPtr param);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int len);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetClassName(IntPtr hwnd, StringBuilder text, int len);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int w, int h, uint flags);
    [DllImport("user32.dll")] private static extern int GetWindowLong(IntPtr hwnd, int index);
    [DllImport("user32.dll")] private static extern int SetWindowLong(IntPtr hwnd, int index, int value);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    public static string Title(IntPtr hwnd) {
        var title = new StringBuilder(512);
        GetWindowText(hwnd, title, title.Capacity);
        return title.ToString();
    }
    public static long[] MinecraftWindows() {
        var handles = new List<long>();
        EnumWindows((hwnd, param) => {
            var title = new StringBuilder(512);
            GetWindowText(hwnd, title, title.Capacity);
            if (title.ToString().IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) < 0)
                return true;
            var className = new StringBuilder(128);
            GetClassName(hwnd, className, className.Capacity);
            if (!className.ToString().StartsWith("GLFW", StringComparison.OrdinalIgnoreCase))
                return true;
            uint pid;
            GetWindowThreadProcessId(hwnd, out pid);
            if (pid == 0) return true;
            try {
                using (var process = Process.GetProcessById((int)pid)) {
                    var name = process.ProcessName;
                    if (name.Equals("java", StringComparison.OrdinalIgnoreCase)
                            || name.Equals("javaw", StringComparison.OrdinalIgnoreCase))
                        handles.Add(hwnd.ToInt64());
                }
            } catch (ArgumentException) {
                // The window closed during enumeration.
            }
            return true;
        }, IntPtr.Zero);
        return handles.ToArray();
    }
    public static void PushBehind(IntPtr hwnd) {
        const uint SWP_NOSIZE = 1, SWP_NOMOVE = 2, SWP_NOACTIVATE = 16, SWP_FRAMECHANGED = 32;
        // WS_EX_NOACTIVATE prevents the test window from becoming the active
        // Windows foreground app even if GLFW requests focus during loading.
        const int GWL_EXSTYLE = -20;
        const int WS_EX_NOACTIVATE = 0x08000000;
        int style = GetWindowLong(hwnd, GWL_EXSTYLE);
        SetWindowLong(hwnd, GWL_EXSTYLE, style | WS_EX_NOACTIVATE);
        SetWindowPos(hwnd, new IntPtr(1), 0, 0, 0, 0,
            SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    }
}
'@

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDir = Join-Path $root 'run/pyrovfx-benchmarks'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir ("secondary-bench-" + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + ".log")
$gradle = Join-Path $root 'gradlew.bat'
$args = @('runClientBench',
    "-PbenchWorld=$World",
    "-PbenchMuseumBuild=$($MuseumBuild.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchScenario=$Scenario",
    "-PbenchWidth=$Width", "-PbenchHeight=$Height",
    '-PbenchSecondMonitorBackground=true',
    "-PbenchUncapped=$($Uncapped.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchDisableCollisionCache=$($DisableCollisionCache.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchRequireGpuAnalytic=$($RequireGpuAnalytic.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyAnalytic=$($ForceCpuAnalytic.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyCpu=$($ForceLegacyCpu.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyRender=$($ForceLegacyRender.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchNoCulling=$($DisableCulling.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchTinyAnalyticThreshold=$TinyAnalyticThreshold",
    "-PbenchRequireGpuInstancing=$($RequireGpuInstancing.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchModelForceLegacy=$($ForceLegacyModels.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchHeavy=$($Heavy.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchGlobalStress=$($GlobalStress.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchGpuTelemetry=$($GpuTelemetry.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchVramProbe=$($ProbeVram.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchSoakTicks=$SoakTicks",
    "-PbenchAdaptive=$($AdaptiveSpawns.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchAdaptiveTargetMs=$AdaptiveCpuTargetMs",
    "-PbenchRenderStateAudit=$($RenderStateAudit.IsPresent.ToString().ToLowerInvariant())",
    '--no-daemon', '--console=plain')

# Separate stdout/err file redirection by cmd, without creating an extra console.
$quotedArgs = ($args | ForEach-Object { '"' + $_.Replace('"','') + '"' }) -join ' '
$cmd = '"' + $gradle + '" ' + $quotedArgs + ' > "' + $log + '" 2>&1'
$psi = [System.Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $env:ComSpec
$psi.Arguments = '/d /s /c "' + $cmd + '"'
$psi.WorkingDirectory = $root
$psi.CreateNoWindow = $true
$psi.UseShellExecute = $false

$foregroundBefore = [PyroBenchDesktop]::GetForegroundWindow()
$foregroundTitle = [PyroBenchDesktop]::Title($foregroundBefore)
$existing = [System.Collections.Generic.HashSet[long]]::new()
foreach ($handle in [PyroBenchDesktop]::MinecraftWindows()) { [void]$existing.Add($handle) }

$process = [System.Diagnostics.Process]::Start($psi)
if ($null -eq $process) { throw 'Failed to launch Gradle process.' }
$seen = [System.Collections.Generic.HashSet[long]]::new()
try {
    while (-not $process.HasExited) {
        foreach ($handle in [PyroBenchDesktop]::MinecraftWindows()) {
            if ($existing.Contains($handle)) { continue }
            if ($seen.Add($handle)) {
                [PyroBenchDesktop]::PushBehind([IntPtr]::new($handle))
            }
        }
        Start-Sleep -Milliseconds 50
    }
    $process.WaitForExit()
    Write-Output "Window focus (never manipulated): before='$foregroundTitle', Java/GLFW Minecraft windows=$($seen.Count), after='$([PyroBenchDesktop]::Title([PyroBenchDesktop]::GetForegroundWindow()))'"
    Write-Output "Secondary-screen benchmark exit=$($process.ExitCode); log=$log"
    if (Test-Path $log) {
        Get-Content $log -Tail 18
    }
    if ($process.ExitCode -ne 0) { throw "Gradle benchmark exited with code $($process.ExitCode)" }
} finally {
    $process.Dispose()
}