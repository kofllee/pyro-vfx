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
    [switch]$RequireGpuInstancing,
    [switch]$ForceLegacyModels,
    [switch]$Heavy,
    [switch]$GpuTelemetry,
    [switch]$ProbeVram,
    [int]$SoakTicks = 600,
    [switch]$AdaptiveSpawns,
    [double]$AdaptiveCpuTargetMs = 0,
    [switch]$RenderStateAudit
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$secondary = @([System.Windows.Forms.Screen]::AllScreens | Where-Object { -not $_.Primary })
if ($secondary.Count -lt 1) { throw 'No secondary monitor; benchmark aborted to protect the active desktop.' }
if ($Width -gt $secondary[0].Bounds.Width -or $Height -gt $secondary[0].Bounds.Height) {
    throw "Benchmark window ${Width}x${Height} does not fit secondary display $($secondary[0].Bounds)."
}

# The temporary Java/GLFW focus on first window creation is a Windows behavior.
# Watchdog immediately pushes only the new Minecraft HWND behind other windows,
# and restores the application that was foreground before launch.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class PyroBenchDesktop {
    public delegate bool EnumWindowsProc(IntPtr hwnd, IntPtr param);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowsProc cb, IntPtr param);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int len);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int w, int h, uint flags);
    [DllImport("user32.dll")] private static extern int GetWindowLong(IntPtr hwnd, int index);
    [DllImport("user32.dll")] private static extern int SetWindowLong(IntPtr hwnd, int index, int value);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, IntPtr pid);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] private static extern bool AttachThreadInput(uint first, uint second, bool attach);
    [DllImport("user32.dll")] private static extern void SwitchToThisWindow(IntPtr hwnd, bool altTab);
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
            if (title.ToString().IndexOf("Minecraft", StringComparison.OrdinalIgnoreCase) >= 0)
                handles.Add(hwnd.ToInt64());
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
    public static bool RestoreForeground(IntPtr previous, IntPtr benchmark) {
        if (previous == IntPtr.Zero || previous == benchmark || !IsWindow(previous)) return false;
        if (GetForegroundWindow() != benchmark) return GetForegroundWindow() == previous;
        uint thread = GetCurrentThreadId();
        uint foregroundThread = GetWindowThreadProcessId(benchmark, IntPtr.Zero);
        bool attached = thread != foregroundThread && AttachThreadInput(thread, foregroundThread, true);
        bool success;
        try {
            success = SetForegroundWindow(previous);
            if (!success) {
                SwitchToThisWindow(previous, true);
                success = GetForegroundWindow() == previous;
            }
        } finally {
            if (attached) AttachThreadInput(thread, foregroundThread, false);
        }
        return success;
    }
}
'@

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDir = Join-Path $root 'run/pyrovfx-benchmarks'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir 'secondary-bench-launch.log'
$gradle = Join-Path $root 'gradlew.bat'
$args = @('runClientBench',
    "-PbenchScenario=$Scenario",
    "-PbenchWidth=$Width", "-PbenchHeight=$Height",
    '-PbenchSecondMonitorBackground=true',
    "-PbenchUncapped=$($Uncapped.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchDisableCollisionCache=$($DisableCollisionCache.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchRequireGpuAnalytic=$($RequireGpuAnalytic.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyAnalytic=$($ForceCpuAnalytic.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyCpu=$($ForceLegacyCpu.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchLegacyRender=$($ForceLegacyRender.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchRequireGpuInstancing=$($RequireGpuInstancing.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchModelForceLegacy=$($ForceLegacyModels.IsPresent.ToString().ToLowerInvariant())",
    "-PbenchHeavy=$($Heavy.IsPresent.ToString().ToLowerInvariant())",
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
$focusAttempts = 0
$focusRestores = 0
try {
    while (-not $process.HasExited) {
        foreach ($handle in [PyroBenchDesktop]::MinecraftWindows()) {
            if ($existing.Contains($handle)) { continue }
            if ($seen.Add($handle)) {
                [PyroBenchDesktop]::PushBehind([IntPtr]::new($handle))
            }
            if ([PyroBenchDesktop]::GetForegroundWindow().ToInt64() -eq $handle) {
                $focusAttempts++
                if ([PyroBenchDesktop]::RestoreForeground($foregroundBefore, [IntPtr]::new($handle))) {
                    $focusRestores++
                }
            }
        }
        Start-Sleep -Milliseconds 50
    }
    $process.WaitForExit()
    Write-Output "Window focus: before='$foregroundTitle', Minecraft windows=$($seen.Count), restore attempts=$focusAttempts, successes=$focusRestores, after='$([PyroBenchDesktop]::Title([PyroBenchDesktop]::GetForegroundWindow()))'"
    Write-Output "Secondary-screen benchmark exit=$($process.ExitCode); log=$log"
    if (Test-Path $log) {
        Get-Content $log -Tail 18
    }
    if ($process.ExitCode -ne 0) { throw "Gradle benchmark exited with code $($process.ExitCode)" }
} finally {
    $process.Dispose()
}