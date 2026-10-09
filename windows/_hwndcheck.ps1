Add-Type @"
using System;using System.Text;using System.Runtime.InteropServices;
public class HwndCheck {
  [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool IsWindowEnabled(IntPtr h);
  [DllImport("user32.dll")] public static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern int GetClassNameW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint flags);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
$exe = 'C:\Users\soham\Downloads\chess\windows\_winprobe2.exe'
if (-not (Test-Path $exe)) { Write-Host "missing _winprobe2.exe"; exit 1 }
$txt = 'C:\Users\soham\Downloads\chess\windows\winprobe.txt'
if (Test-Path $txt) { Remove-Item $txt }
$p = Start-Process -FilePath $exe -RedirectStandardOutput 'C:\Users\soham\Downloads\chess\windows\_winprobe2.log' -PassThru
$handle = $null
for ($i = 0; $i -lt 40 -and -not $handle; $i++) {
    Start-Sleep -Milliseconds 250
    if (Test-Path $txt) {
        $m = Select-String -Path $txt -Pattern 'OS_HANDLE=(\d+)' -ErrorAction SilentlyContinue
        if ($m) { $handle = [int64]$m.Matches[0].Groups[1].Value }
    }
}
Write-Host "probe handle from log: $handle"
if ($handle) {
    $h = [IntPtr]$handle
    Write-Host ("IsWindow=" + [HwndCheck]::IsWindow($h))
    Write-Host ("IsWindowVisible=" + [HwndCheck]::IsWindowVisible($h))
    $sb = New-Object System.Text.StringBuilder 256
    [void][HwndCheck]::GetWindowTextW($h, $sb, 256)
    Write-Host ("Title=[" + $sb.ToString() + "]")
    $cbx = New-Object System.Text.StringBuilder 256
    [void][HwndCheck]::GetClassNameW($h, $cbx, 256)
    Write-Host ("Class=[" + $cbx.ToString() + "]")
    [uint32]$opid = 0
    [void][HwndCheck]::GetWindowThreadProcessId($h, [ref]$opid)
    Write-Host "OwnerPid=$opid (probe pid=$($p.Id))"
    $r = New-Object HwndCheck+RECT
    if ([HwndCheck]::GetWindowRect($h, [ref]$r)) { Write-Host ("Rect=" + $r.Left + "," + $r.Top + " " + $r.Right + "," + $r.Bottom) }
} else {
    Write-Host "probe never printed an OS handle"
}
Write-Host "--- probe file ---"
if (Test-Path $txt) { Get-Content $txt }
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
