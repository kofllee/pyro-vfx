param(
    [Parameter(Mandatory=$true)][string]$Baseline,
    [Parameter(Mandatory=$true)][string]$Candidate
)
$ErrorActionPreference = 'Stop'
$base = Get-Content $Baseline -Raw | ConvertFrom-Json
$next = Get-Content $Candidate -Raw | ConvertFrom-Json
foreach ($key in @('scenario', 'effectSha256', 'seed', 'requestedParticles')) {
    if ($base.$key -ne $next.$key) { throw "Nonmatching benchmark $key" }
}
if ($base.status -ne 'completed' -or $next.status -ne 'completed') { throw 'Benchmark not completed' }
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class PyroImageDiff {
    public static string Compare(string left, string right) {
        using (var a = new Bitmap(left))
        using (var b = new Bitmap(right)) {
            if (a.Width != b.Width || a.Height != b.Height) throw new Exception("Image dimensions mismatch");
            var rect = new Rectangle(0, 0, a.Width, a.Height);
            var x = a.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            var y = b.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            try {
                int n = Math.Abs(x.Stride) * a.Height;
                byte[] aa = new byte[n], bb = new byte[n];
                Marshal.Copy(x.Scan0, aa, 0, n);
                Marshal.Copy(y.Scan0, bb, 0, n);
                long changed = 0, absolute = 0, maximum = 0;
                for (int i = 0; i < n; i += 4) {
                    int delta = Math.Abs(aa[i] - bb[i]) + Math.Abs(aa[i+1] - bb[i+1])
                                + Math.Abs(aa[i+2] - bb[i+2]);
                    absolute += delta;
                    if (delta != 0) changed++;
                    if (delta > maximum) maximum = delta;
                }
                double total = (double)a.Width * a.Height;
                return String.Format(System.Globalization.CultureInfo.InvariantCulture,
                    "resolution={0}x{1} differentPixels={2}/{3} ({4:F4}%) meanAbsoluteChannelError={5:F6} maxPixelRgbDelta={6}",
                    a.Width,a.Height,changed,(long)total,changed*100/total,absolute/(total*3),maximum);
            } finally { a.UnlockBits(x); b.UnlockBits(y); }
        }
    }
}
'@
[PyroImageDiff]::Compare($base.screenshot, $next.screenshot)
