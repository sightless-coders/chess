# _drive_test.ps1 - drives chess.exe with real keystrokes and captures what it prints.
param([string]$Mode = "offline")
Add-Type @"
using System;using System.Runtime.InteropServices;
public class Inj {
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
    uint dummy = 0;
    uint fgThread = 0;
    if (fg != IntPtr.Zero) fgThread = GetWindowThreadProcessId(fg, out dummy);
    uint myThread = GetCurrentThreadId();
    bool attached = false;
    if (fgThread != 0 && fgThread != myThread) { attached = AttachThreadInput(myThread, fgThread, true); }
    ShowWindow(h, 9);
    SetForegroundWindow(h);
    System.Threading.Thread.Sleep(80);
    if (attached) AttachThreadInput(myThread, fgThread, false);
    return GetForegroundWindow() == h;
  }
  public const byte VK_ESCAPE = 0x1B;
  public const byte VK_RETURN = 0x0D;
  public const byte VK_SPACE  = 0x20;
  public const byte VK_LEFT   = 0x25;
  public const byte VK_UP     = 0x26;
  public const byte VK_RIGHT  = 0x27;
  public const byte VK_DOWN   = 0x28;
  public const byte VK_LSHIFT = 0xA0;
  public const byte VK_LCTRL  = 0xA2;
  public const byte VK_1 = 0x31; public const byte VK_2 = 0x32;
  public const byte VK_3 = 0x33; public const byte VK_4 = 0x34;
  public const byte VK_N = 0x4E; public const byte VK_U = 0x55;
  public const byte VK_L = 0x4C; public const byte VK_M = 0x4D;
  public const byte VK_C = 0x43; public const byte VK_R = 0x52;
  public static void Down(byte vk) { keybd_event(vk, 0, 0, 0); }
  public static void Up(byte vk)   { keybd_event(vk, 0, 2, 0); }
  public static void Tap(byte vk)  { Down(vk); Up(vk); }
  public static void Chord(byte mod, byte vk) { Down(mod); Down(vk); Up(vk); Up(mod); }
}
"@
$exe  = "C:\Users\soham\Downloads\chess\windows\chess.exe"
$dir  = "C:\Users\soham\Downloads\chess\windows"
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$log  = Join-Path $dir "_test_${Mode}_$stamp.log"
$errl = Join-Path $dir "_test_${Mode}_$stamp.err"

$p = Start-Process -FilePath $exe -RedirectStandardOutput $log -RedirectStandardError $errl -PassThru
$script:failed = $false

function Find-Hwnd($procId) {
    $script:foundHwnd = [IntPtr]::Zero
    $cb = [Inj+EnumProc]{
        param($h, $l)
        [uint32]$holder = 0
        [void][Inj]::GetWindowThreadProcessId($h, [ref]$holder)
        if ($holder -eq $procId) { $script:foundHwnd = $h; return $false }
        return $true
    }
    [void][Inj]::EnumWindows($cb, [IntPtr]::Zero)
    [GC]::KeepAlive($cb)
    return $script:foundHwnd
}

$hwnd = [IntPtr]::Zero
for ($i = 0; $i -lt 40 -and $hwnd -eq [IntPtr]::Zero; $i++) {
    Start-Sleep -Milliseconds 250
    if ($p.HasExited) { break }
    $hwnd = Find-Hwnd $p.Id
}
if ($hwnd -eq [IntPtr]::Zero) {
    Write-Host "FAILED: no window for chess.exe (pid $($p.Id))"
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
    exit 2
}
Write-Host "game window found: $hwnd (pid $($p.Id))"

# Wait until the start-up question loop is definitely running.
$elapsedMs = ((Get-Date) - $p.StartTime).TotalMilliseconds
if ($elapsedMs -lt 2600) { Start-Sleep -Milliseconds ([int](2600 - $elapsedMs)) }

function Test-GameFocused() {
    $fg = [Inj]::GetForegroundWindow()
    if ($fg -eq [IntPtr]::Zero) { return $false }
    [uint32]$owner = 0
    [void][Inj]::GetWindowThreadProcessId($fg, [ref]$owner)
    return ($owner -eq $script:p.Id)
}

$vkMap = @{
    "ESC" = 0x1B; "ENTER" = 0x0D; "SPACE" = 0x20
    "LEFT" = 0x25; "UP" = 0x26; "RIGHT" = 0x27; "DOWN" = 0x28
    "1" = 0x31; "2" = 0x32; "3" = 0x33; "4" = 0x34
    "N" = 0x4E; "U" = 0x55; "L" = 0x4C; "M" = 0x4D; "C" = 0x43; "R" = 0x52
}

# K "ENTER" | "SHIFT+LEFT" | "CTRL+RIGHT" | "SPACE" | "M" ...
function K([string]$spec, [int]$delay) {
    if ($script:failed) { return }
    if (-not (Test-GameFocused)) {
        [void][Inj]::ForceForeground($hwnd)
        Start-Sleep -Milliseconds 150
        if (-not (Test-GameFocused)) {
            [Inj]::DownB(0x12)   # alt trick: allows a process to take foreground
            [Inj]::UpB(0x12)
            [void][Inj]::ForceForeground($hwnd)
            Start-Sleep -Milliseconds 200
        }
        if (-not (Test-GameFocused)) {
            Write-Host "  !! cannot focus the game window for [$spec] - aborting (no stray keys)"
            $script:failed = $true
            return
        }
    }
    $parts = $spec.Split("+")
    try {
        if ($parts.Length -eq 2) {
            $mod = [byte]$vkMap[$parts[0].ToUpper()]
            $key = [byte]$vkMap[$parts[1].ToUpper()]
            [Inj]::DownB($mod)
            Start-Sleep -Milliseconds 60
            [Inj]::DownB($key)
            Start-Sleep -Milliseconds 80
            [Inj]::UpB($key)
            Start-Sleep -Milliseconds 40
            [Inj]::UpB($mod)
        } else {
            $key = [byte]$vkMap[$spec.ToUpper()]
            [Inj]::DownB($key)
            Start-Sleep -Milliseconds 80
            [Inj]::UpB($key)
        }
    } catch {
        Write-Host "  !! bad key spec [$spec]: $($_.Exception.Message)"
        $script:failed = $true
        return
    }
    Start-Sleep -Milliseconds $delay
    $script:p.Refresh()
    $state = "alive"
    if ($script:p.HasExited) { $state = "EXITED" }
    Write-Host "  sent [$spec] -> $state"
}

Write-Host "sending keys ($Mode)..."
if ($Mode -eq "offline") {
    K "SPACE" 800          # skip the logo
    K "ENTER" 250          # first enter of the blind answer (inside the 450ms window)
    K "ENTER" 2500         # second enter -> blind mode, main menu opens
    K "DOWN"  600          # menu: two players on this keyboard
    K "ENTER" 1800         # match starts
    K "ENTER" 600          # pick up the e2 pawn
    K "UP" 350
    K "UP" 500
    K "ENTER" 1400         # 1. e4
    K "UP" 350
    K "UP" 450             # e4 -> e6
    K "SHIFT+LEFT" 600     # diagonal: e6 -> d7
    K "ENTER" 550          # pick up the d7 pawn
    K "DOWN" 350
    K "DOWN" 500           # d7 -> d5
    K "ENTER" 1400         # 1... d5
    K "CTRL+RIGHT" 600     # diagonal: d5 -> e4
    K "ENTER" 550          # pick up the e4 pawn
    K "SHIFT+LEFT" 600     # diagonal: e4 -> d5
    K "ENTER" 1600         # 2. exd5
    K "L" 2500             # hear the move list
    K "M" 1500             # back to the main menu
} elseif ($Mode -eq "host") {
    K "SPACE" 800
    K "ENTER" 250
    K "ENTER" 2500
    K "DOWN" 500
    K "DOWN" 500           # menu: host an online game
    K "ENTER" 5000         # start hosting and wait for the joiner
    K "ENTER" 600          # pick up e2 (host plays White)
    K "UP" 350
    K "UP" 500
    K "ENTER" 3000         # 1. e4 -> sent to the opponent
} elseif ($Mode -eq "join") {
    K "SPACE" 800
    K "ENTER" 250
    K "ENTER" 2500
    K "DOWN" 500
    K "DOWN" 500
    K "DOWN" 500           # menu: join an online game
    K "ENTER" 3500         # address form opens (prefilled 127.0.0.1)
    K "ENTER" 12000        # submit and wait for the host's move
}

if (-not $script:failed) {
    # Quit cleanly so stdout is flushed into the log.
    Start-Sleep -Milliseconds 300
    $p.Refresh()
    if (-not $p.HasExited) { K "ESC" 500 }
    $p.Refresh()
    if (-not $p.HasExited) { K "ESC" 2500 }
    try { $p.WaitForExit(8000) | Out-Null } catch {}
    $p.Refresh()
    if (-not $p.HasExited) {
        Stop-Process -Id $p.Id -Force
        Write-Host "  (forced stop - clean quit did not happen)"
    } else {
        Write-Host "  (process ended)"
    }
}

Write-Host "===== $Mode LOG ($log) ====="
if (Test-Path $log) { Get-Content $log } else { Write-Host "(no log)" }
Write-Host "===== end ($Mode) ====="
if (Test-Path $errl) {
    $e = Get-Content $errl -Raw
    if ($e) { Write-Host "STDERR: $e" }
}
