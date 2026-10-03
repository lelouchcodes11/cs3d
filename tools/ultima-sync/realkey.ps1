param([string]$Keys = "RIGHT", [int]$Port = 8765, [int]$Pause = 700, [string]$Shot = "")
# Sends real keyboard input (keybd_event) to the dev instance's window; refuses when it is not the foreground window.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class K {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, int flags, IntPtr extra);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
}
"@
$ownerPid = (Get-NetTCPConnection -LocalPort $Port -State Listen).OwningProcess
$top = (Get-Process -Id $ownerPid).MainWindowHandle
if ([K]::IsIconic($top)) { [K]::ShowWindow($top, 9) | Out-Null }
# bring to the front: topmost for a moment, then back to normal
[K]::SetWindowPos($top, [IntPtr]-1, 0, 0, 0, 0, 0x13) | Out-Null
[K]::SetForegroundWindow($top) | Out-Null
Start-Sleep -Milliseconds 500
[K]::SetWindowPos($top, [IntPtr]-2, 0, 0, 0, 0, 0x13) | Out-Null
if ([K]::GetForegroundWindow() -ne $top) { "REFUSED: not foreground"; exit 1 }
$map = @{ "RIGHT"=0x27; "LEFT"=0x25; "UP"=0x26; "DOWN"=0x28; "SPACE"=0x20; "M"=0x4D; "I"=0x49; "F"=0x46; "F11"=0x7A; "ESC"=0x1B; "PERIOD"=0xBE; "COMMA"=0xBC; "J"=0x4A; "L"=0x4C }
foreach ($k in $Keys.Split(",")) {
  $vk = [byte]$map[$k.ToUpper()]
  $ext = if (@(0x25,0x26,0x27,0x28) -contains $vk) { 1 } else { 0 }
  [K]::keybd_event($vk, 0, $ext, [IntPtr]::Zero); Start-Sleep -Milliseconds 50
  [K]::keybd_event($vk, 0, ($ext -bor 2), [IntPtr]::Zero)
  if ($Shot -ne "") { Start-Sleep -Milliseconds 120; Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/screenshot" -OutFile $Shot }
  Start-Sleep -Milliseconds $Pause
  "sent $k"
}
