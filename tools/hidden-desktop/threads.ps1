# CPU per thread name (ms of CPU per second) of two test apps over the SAME seconds: background load on the PC hits both alike, which makes an
# A/B of two builds fair (whole-process CPU of back-to-back runs varied 2x for the same build).
#   tools/hidden-desktop/threads.ps1 -PortA 8771 -PortB 8772 -Secs 60
param([int]$PortA = 8771, [int]$PortB = 8772, [int]$Secs = 60, [int]$Top = 14)
Add-Type @"
using System; using System.Runtime.InteropServices;
public class ThreadCpu {
  [DllImport("kernel32.dll")] static extern IntPtr OpenThread(int access, bool inherit, int id);
  [DllImport("kernel32.dll")] static extern int GetThreadDescription(IntPtr h, out IntPtr desc);
  [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
  [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr p);
  [DllImport("kernel32.dll")] static extern bool GetThreadTimes(IntPtr h, out long c, out long e, out long k, out long u);
  public static string Name(int id) { IntPtr h = OpenThread(0x1800, false, id); if (h == IntPtr.Zero) return "?"; IntPtr d; string s = "?"; if (GetThreadDescription(h, out d) >= 0) { s = Marshal.PtrToStringUni(d); LocalFree(d); } CloseHandle(h); return s; }
  public static long Cpu(int id) { IntPtr h = OpenThread(0x1800, false, id); if (h == IntPtr.Zero) return -1; long c, e, k, u; GetThreadTimes(h, out c, out e, out k, out u); CloseHandle(h); return k + u; }
}
"@
function Snap($procId) { $m = @{}; foreach ($t in (Get-Process -Id $procId).Threads) { $m[$t.Id] = [ThreadCpu]::Cpu($t.Id) }; $m }
function PidOf($port) { (Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object { $_.CommandLine -like "*args-$port.txt*" } | Select-Object -First 1).ProcessId }
function ByName($s0, $s1) {
  $g = @{}
  foreach ($id in $s1.Keys) {
    $d = $s1[$id] - $(if ($s0.ContainsKey($id)) { $s0[$id] } else { 0 }); if ($d -le 0) { continue }
    $n = [ThreadCpu]::Name($id) -replace '[0-9]+$', '#' -replace '-[0-9]+', '-#'; if (-not $n) { $n = "(unnamed)" }
    $g[$n] = $g[$n] + $d
  }
  $g
}
$pa = PidOf $PortA; $pb = PidOf $PortB
$a0 = Snap $pa; $b0 = Snap $pb; Start-Sleep $Secs; $a1 = Snap $pa; $b1 = Snap $pb
$ga = ByName $a0 $a1; $gb = ByName $b0 $b1
$names = @($ga.Keys) + @($gb.Keys) | Sort-Object -Unique | Sort-Object { - [Math]::Max($ga[$_], $gb[$_]) } | Select-Object -First $Top
"{0,-44} {1,10} {2,10}" -f "thread (ms CPU per s)", "A $PortA", "B $PortB"
foreach ($n in $names) { "{0,-44} {1,10:N1} {2,10:N1}" -f $n, ($ga[$n] / 1e4 / $Secs), ($gb[$n] / 1e4 / $Secs) }
"{0,-44} {1,10:N1} {2,10:N1}" -f "TOTAL", (($ga.Values | Measure-Object -Sum).Sum / 1e4 / $Secs), (($gb.Values | Measure-Object -Sum).Sum / 1e4 / $Secs)
