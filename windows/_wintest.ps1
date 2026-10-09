Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;using System.Text;using System.Runtime.InteropServices;
public class WinEnum2 {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern int GetWindowTextW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern int GetClassNameW(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
}
"@
# 1. Can this session create a normal window at all?
$f = New-Object System.Windows.Forms.Form
$f.Text = "AgentFormTest"
$f.Show()
$f.Refresh()
Start-Sleep -Milliseconds 500
$myPid = $PID
Write-Host "form shown in pid $myPid"
$seen = @()
$cb = [WinEnum2+EnumProc]{
  param($h, $l)
  [uint32]$holder = 0
  [void][WinEnum2]::GetWindowThreadProcessId($h, [ref]$holder)
  if ($holder -eq $myPid) {
    $sb = New-Object System.Text.StringBuilder 256
    [void][WinEnum2]::GetWindowTextW($h, $sb, 256)
    $script:seen += "  MYWINDOW hwnd=$h visible=" + [WinEnum2]::IsWindowVisible($h) + " title=[" + $sb + "]"
  }
  return $true
}
[void][WinEnum2]::EnumWindows($cb, [IntPtr]::Zero)
$seen | ForEach-Object { Write-Host $_ }
if (-not $seen) { Write-Host "NO WINDOW FROM NORMAL WINFORMS EITHER" }
$f.Close()

# 2. Does an NVGT program with show_window create one?
$exe = 'C:\Users\soham\Downloads\chess\windows\_winprobe.exe'
if (-not (Test-Path $exe)) { Write-Host "probe exe missing - compile first"; exit 1 }
$p = Start-Process -FilePath $exe -RedirectStandardOutput 'C:\Users\soham\Downloads\chess\windows\_winprobe.log' -PassThru
Start-Sleep -Seconds 2
$target = $p.Id
$seen2 = @()
$cb2 = [WinEnum2+EnumProc]{
  param($h, $l)
  [uint32]$holder = 0
  [void][WinEnum2]::GetWindowThreadProcessId($h, [ref]$holder)
  if ($holder -eq $target) {
    $sb = New-Object System.Text.StringBuilder 256
    [void][WinEnum2]::GetWindowTextW($h, $sb, 256)
    $cbx = New-Object System.Text.StringBuilder 256
    [void][WinEnum2]::GetClassNameW($h, $cbx, 256)
    $script:seen2 += "  NVGTWINDOW hwnd=$h visible=" + [WinEnum2]::IsWindowVisible($h) + " class=[" + $cbx + "] title=[" + $sb + "]"
  }
  return $true
}
[void][WinEnum2]::EnumWindows($cb2, [IntPtr]::Zero)
$seen2 | ForEach-Object { Write-Host $_ }
if (-not $seen2) { Write-Host "NVGT show_window produced NO top-level window" }
try { $p.WaitForExit(6000) | Out-Null } catch {}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
Write-Host "--- probe log ---"
if (Test-Path 'C:\Users\soham\Downloads\chess\windows\_winprobe.log') { Get-Content 'C:\Users\soham\Downloads\chess\windows\_winprobe.log' }
