Add-Type @"
using System;using System.Runtime.InteropServices;
public class Inj2 {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, int extra);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint uCode, uint uMapType);
  [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
  [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
  public static void DownB(byte vk) { keybd_event(vk, (byte)MapVirtualKey(vk, 0), 0, 0); }
  public static void UpB(byte vk)   { keybd_event(vk, (byte)MapVirtualKey(vk, 0), 2, 0); }
  public static bool ForceForeground(IntPtr h) {
    if (GetForegroundWindow() == h) return true;
    IntPtr fg = GetForegroundWindow();
    uint dummy = 0; uint fgThread = 0;
    if (fg != IntPtr.Zero) fgThread = GetWindowThreadProcessId(fg, out dummy);
    uint myThread = GetCurrentThreadId();
    bool attached = false;
    if (fgThread != 0 && fgThread != myThread) attached = AttachThreadInput(myThread, fgThread, true);
    ShowWindow(h, 9);
    SetForegroundWindow(h);
    System.Threading.Thread.Sleep(80);
    if (attached) AttachThreadInput(myThread, fgThread, false);
    return GetForegroundWindow() == h;
  }
}
"@
$exe = 'C:\Users\soham\Downloads\chess\windows\_keyprobe.exe'
$log = 'C:\Users\soham\Downloads\chess\windows\_keyprobe_out.log'
if (Test-Path $log) { Remove-Item $log -Force -ErrorAction SilentlyContinue }
$p = Start-Process -FilePath $exe -RedirectStandardOutput $log -PassThru
$hwnd = [IntPtr]::Zero
$cb = [Inj2+EnumProc]{
    param($h, $l)
    [uint32]$holder = 0
    [void][Inj2]::GetWindowThreadProcessId($h, [ref]$holder)
    if ($holder -eq $script:p.Id) { $script:hwnd = $h; return $false }
    return $true
}
for ($i = 0; $i -lt 40 -and $hwnd -eq [IntPtr]::Zero; $i++) {
    Start-Sleep -Milliseconds 250
    if ($p.HasExited) { break }
    [void][Inj2]::EnumWindows($cb, [IntPtr]::Zero)
}
[GC]::KeepAlive($cb)
if ($hwnd -eq [IntPtr]::Zero) { Write-Host "no window"; Stop-Process -Id $p.Id -Force; exit 2 }
Start-Sleep -Milliseconds 800
[void][Inj2]::ForceForeground($hwnd)
Start-Sleep -Milliseconds 300
Write-Host ("focused=" + ([Inj2]::GetForegroundWindow() -eq $hwnd))

function Tap([byte]$vk) {
    [void][Inj2]::ForceForeground($script:hwnd)
    [Inj2]::DownB($vk)
    Start-Sleep -Milliseconds 80
    [Inj2]::UpB($vk)
    Start-Sleep -Milliseconds 300
}
Tap 0x20   # space
Tap 0x0D   # enter
Tap 0x0D   # enter
Tap 0x26   # up
Tap 0x28   # down
Tap 0x25   # left
Tap 0x1B   # escape -> probe exits

try { $p.WaitForExit(8000) | Out-Null } catch {}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force; Write-Host "(forced)" }
Write-Host "--- probe output ---"
if (Test-Path $log) { Get-Content $log } else { Write-Host "(no log)" }
