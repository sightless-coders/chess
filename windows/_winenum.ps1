Add-Type @"
using System;using System.Text;using System.Runtime.InteropServices;
public class WinEnum {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern int GetClassNameW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
}
"@
$p = Start-Process -FilePath 'C:\Users\soham\Downloads\chess\windows\chess.exe' -RedirectStandardOutput 'C:\Users\soham\Downloads\chess\windows\_dbg.log' -PassThru
Start-Sleep -Seconds 3
$target = $p.Id
Write-Host "pid=$target"
$cb = [WinEnum+EnumProc]{
  param($h, $l)
  [uint32]$holder = 0
  [void][WinEnum]::GetWindowThreadProcessId($h, [ref]$holder)
  if ($holder -eq $target) {
    $sb = New-Object System.Text.StringBuilder 256
    [void][WinEnum]::GetWindowTextW($h, $sb, 256)
    $cbx = New-Object System.Text.StringBuilder 256
    [void][WinEnum]::GetClassNameW($h, $cbx, 256)
    Write-Host ("hwnd=$h visible=" + [WinEnum]::IsWindowVisible($h) + " class=[" + $cbx + "] title=[" + $sb + "]")
  }
  return $true
}
[void][WinEnum]::EnumWindows($cb, [IntPtr]::Zero)
Write-Host "--- enum done ---"
Stop-Process -Id $p.Id -Force
Start-Sleep -Milliseconds 300
Write-Host "--- log ---"
if (Test-Path 'C:\Users\soham\Downloads\chess\windows\_dbg.log') { Get-Content 'C:\Users\soham\Downloads\chess\windows\_dbg.log' }
