param([string]$Text = "abc", [int]$Port = 8765, [switch]$Paste, [string]$Shot = "")
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class T {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, int flags, IntPtr extra);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern short VkKeyScan(char c);
}
"@
$ownerPid = (Get-NetTCPConnection -LocalPort $Port -State Listen).OwningProcess
$top = (Get-Process -Id $ownerPid).MainWindowHandle
[T]::SetWindowPos($top, [IntPtr]-1, 0, 0, 0, 0, 0x13) | Out-Null
[T]::SetForegroundWindow($top) | Out-Null
Start-Sleep -Milliseconds 400
[T]::SetWindowPos($top, [IntPtr]-2, 0, 0, 0, 0, 0x13) | Out-Null
if ([T]::GetForegroundWindow() -ne $top) { "REFUSED: not foreground"; exit 1 }
if ($Paste) {
  Set-Clipboard -Value $Text
  [T]::keybd_event(0x11, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 40
  [T]::keybd_event(0x56, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 40
  [T]::keybd_event(0x56, 0, 2, [IntPtr]::Zero); [T]::keybd_event(0x11, 0, 2, [IntPtr]::Zero)
  "pasted"
} else {
  foreach ($ch in $Text.ToCharArray()) {
    $r = [T]::VkKeyScan($ch)
    $vk = [byte]($r -band 0xFF); $shift = (($r -shr 8) -band 1) -ne 0
    if ($shift) { [T]::keybd_event(0x10, 0, 0, [IntPtr]::Zero) }
    [T]::keybd_event($vk, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 25
    [T]::keybd_event($vk, 0, 2, [IntPtr]::Zero)
    if ($shift) { [T]::keybd_event(0x10, 0, 2, [IntPtr]::Zero) }
    Start-Sleep -Milliseconds 25
  }
  "typed"
}
Start-Sleep -Milliseconds 400
if ($Shot -ne "") { Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/screenshot" -OutFile $Shot }
