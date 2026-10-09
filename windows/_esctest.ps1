Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;using System.Runtime.InteropServices;
public class FG2 {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
}
"@
$exe = 'C:\Users\soham\Downloads\chess\windows\chess.exe'
$p = Start-Process -FilePath $exe -RedirectStandardOutput 'C:\Users\soham\Downloads\chess\windows\_esctest.log' -PassThru
$hwnd = [IntPtr]::Zero
$cb = [FG2+EnumProc]{
    param($h, $l)
    [uint32]$holder = 0
    [void][FG2]::GetWindowThreadProcessId($h, [ref]$holder)
    if ($holder -eq $script:p.Id) { $script:hwnd = $h; return $false }
    return $true
}
for ($i = 0; $i -lt 40 -and $hwnd -eq [IntPtr]::Zero; $i++) {
    Start-Sleep -Milliseconds 250
    [void][FG2]::EnumWindows($cb, [IntPtr]::Zero)
}
[GC]::KeepAlive($cb)
Write-Host "hwnd=$hwnd"
if ($hwnd -eq [IntPtr]::Zero) { Stop-Process -Id $p.Id -Force; exit 2 }
[void][FG2]::SetForegroundWindow($hwnd)
Start-Sleep -Milliseconds 300
Write-Host ("focused=" + ([FG2]::GetForegroundWindow() -eq $hwnd))
[System.Windows.Forms.SendKeys]::SendWait("{ESC}")
Start-Sleep -Seconds 3
Write-Host ("exited=" + $p.HasExited)
if (-not $p.HasExited) {
    # try again in case the first key landed before the loop started
    [void][FG2]::SetForegroundWindow($hwnd)
    Start-Sleep -Milliseconds 300
    [System.Windows.Forms.SendKeys]::SendWait("{ESC}")
    Start-Sleep -Seconds 3
    Write-Host ("exited_after_second=" + $p.HasExited)
}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force; Write-Host "(forced)" }
