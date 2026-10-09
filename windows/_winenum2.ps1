Add-Type @"
using System;using System.Text;using System.Runtime.InteropServices;
public class WinEnum3 {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern int GetClassNameW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
}
"@
$exe = 'C:\Users\soham\Downloads\chess\windows\_winprobe2.exe'
if (-not (Test-Path $exe)) { Write-Host "missing _winprobe2.exe - compile it first"; exit 1 }
Add-Type -AssemblyName System.Windows.Forms
$frm = New-Object System.Windows.Forms.Form
$frm.Text = "ControlForm"
$frm.Show()
$frm.Refresh()
$p = Start-Process -FilePath $exe -RedirectStandardOutput 'C:\Users\soham\Downloads\chess\windows\_winprobe2.log' -PassThru
Start-Sleep -Seconds 3
Write-Host ("nvgt alive=" + (-not $p.HasExited))
$targets = @{ $p.Id = "NVGT"; $PID = "SELF" }
$found = @{}
$cb = [WinEnum3+EnumProc]{
  param($h, $l)
  [uint32]$holder = 0
  [void][WinEnum3]::GetWindowThreadProcessId($h, [ref]$holder)
  if ($targets.ContainsKey([int]$holder)) {
    $sb = New-Object System.Text.StringBuilder 256
    [void][WinEnum3]::GetWindowTextW($h, $sb, 256)
    $cbx = New-Object System.Text.StringBuilder 256
    [void][WinEnum3]::GetClassNameW($h, $cbx, 256)
    Write-Host ("  [" + $targets[[int]$holder] + "] hwnd=$h visible=" + [WinEnum3]::IsWindowVisible($h) + " class=[" + $cbx + "] title=[" + $sb + "]")
    $script:found[[int]$holder] = 1
  }
  return $true
}
[void][WinEnum3]::EnumWindows($cb, [IntPtr]::Zero)
if (-not $found.ContainsKey($PID)) { Write-Host "  SELF CONTROL FAILED (callback broken?)" }
if (-not $found.ContainsKey($p.Id)) { Write-Host "  NVGT process has NO enumerable top-level window" }
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
$frm.Close()
