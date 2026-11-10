# Starts the development build on a separate Windows desktop ("cs3d-hidden") that is never shown: its windows can not cover or take the focus from
# the person using the PC, yet they draw (Direct3D), play video and answer the dev server like a normal window. Real mouse and keyboard can not
# reach it; drive it with the dev server (/nav, /playurl, /screenshot?sk=1, /videostats, /quit).
#
#   tools/hidden-desktop/start.ps1 -Port 8771 -Data <data folder> [-Jar <android.jar copy>] [-Extra "-Dx=y -Dz=w"]
#
# Prints the process id. Needs a built classpath: run classpath.ps1 once after building (it writes build/hidden-desktop/classpath.txt).
# -Jar swaps android/build/libs/android.jar for another copy: two builds can then run at once for a fair A/B (see threads.ps1).
# Never point -Data at real user data: copy it, and remove sign-ins and sync addresses first.
param([int]$Port = 8771, [Parameter(Mandatory = $true)][string]$Data, [string]$Jar = "", [string]$Extra = "")
$ErrorActionPreference = "Stop"
$desktop = "cs3d-hidden"
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$work = Join-Path $root "build\hidden-desktop"
$cpFile = Join-Path $work "classpath.txt"
if (-not (Test-Path $cpFile)) { throw "run tools/hidden-desktop/classpath.ps1 first" }
Add-Type @"
using System; using System.Runtime.InteropServices;
public class HiddenDesk {
  [DllImport("user32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern IntPtr CreateDesktopW(string name, IntPtr dev, IntPtr mode, uint flags, uint access, ref SA sa);
  [StructLayout(LayoutKind.Sequential)] public struct SA { public int len; public IntPtr sd; public bool inherit; }
  [DllImport("advapi32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool ConvertStringSecurityDescriptorToSecurityDescriptorW(string sddl, int rev, out IntPtr sd, IntPtr size);
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] public struct SI { public int cb; public string r; public string desk; public string title; public int x,y,w,h,cx,cy,fill; public int flags; public short show, r2; public IntPtr r3, i, o, e; }
  [StructLayout(LayoutKind.Sequential)] public struct PI { public IntPtr hp, ht; public int pid, tid; }
  [DllImport("kernel32.dll", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool CreateProcessW(string app, System.Text.StringBuilder cmd, IntPtr pa, IntPtr ta, bool inherit, uint flags, IntPtr env, string dir, ref SI si, out PI pi);
}
"@
# GENERIC_ALL; the desktop lives as long as a process uses it
# who may use the desktop: this user, SYSTEM and ALL APPLICATION PACKAGES (AC). Without AC the extension sandbox (an AppContainer process) can not
# attach to the desktop and dies at start with 0xC0000142 (STATUS_DLL_INIT_FAILED); the normal desktop grants it.
$me = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$sd = [IntPtr]::Zero
if (-not [HiddenDesk]::ConvertStringSecurityDescriptorToSecurityDescriptorW("D:(A;;GA;;;$me)(A;;GA;;;SY)(A;;GA;;;AC)", 1, [ref]$sd, [IntPtr]::Zero)) { throw "security descriptor" }
$sa = New-Object HiddenDesk+SA; $sa.len = [Runtime.InteropServices.Marshal]::SizeOf($sa); $sa.sd = $sd
if ([HiddenDesk]::CreateDesktopW($desktop, [IntPtr]::Zero, [IntPtr]::Zero, 0, 0x10000000, [ref]$sa) -eq [IntPtr]::Zero) { throw "CreateDesktop failed" }
New-Item -ItemType Directory -Force $Data | Out-Null
$fwd = { param($p) $p.Replace([char]92, [char]47) }
$cp = (Get-Content $cpFile -Raw).Trim()
if ($Jar) { $cp = $cp.Replace((Join-Path $root "android\build\libs\android.jar"), $Jar) }
$opts = @("-Xmx3g", "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
  "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
  "-Xss4m", "-Dfile.encoding=UTF-8",
  # as the installed app starts (app/build.gradle.kts jpackage options): the extension sandbox needs the security manager allowed
  "-Djava.security.manager=allow", "-Dsun.java2d.d3d=false", "-XX:+ExplicitGCInvokesConcurrent", "-XX:MaxHeapFreeRatio=30", "-XX:MinHeapFreeRatio=10", "-XX:G1PeriodicGCInterval=30000", "`"-Dcloudstream.data=$(& $fwd (Resolve-Path $Data).Path)`"", "-Dcloudstream.devport=$Port", "-Dcloudstream.ipcport=$($Port + 44000)",
  # no GitHub release checks (60 a hour per address), silent players, mpv-2.dll and WebView2Loader.dll from the project
  "-Dcloudstream.updateApi=http://127.0.0.1:1/", "-Dcloudstream.mute=true", "`"-Djna.library.path=$(& $fwd (Join-Path $root 'app\native\windows-x64'))`"",
  # where the installed app keeps its native files (WebView2 loader, mpv): without it a run outside the project folder falls back to JCEF pages
  "`"-Dcompose.application.resources.dir=$(& $fwd (Join-Path $root 'app\native\windows-x64'))`"")
if ($Extra) { $opts += $Extra.Split(" ", [StringSplitOptions]::RemoveEmptyEntries) }
$argfile = Join-Path $work "args-$Port.txt"
[IO.File]::WriteAllLines($argfile, $opts + @("-cp", "`"$(& $fwd $cp)`"", "com.lagradost.desktop.MainKt"))
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" }
$si = New-Object HiddenDesk+SI; $si.cb = [Runtime.InteropServices.Marshal]::SizeOf($si); $si.desk = $desktop
$pi = New-Object HiddenDesk+PI
$cmd = New-Object System.Text.StringBuilder "`"$java`" @`"$argfile`""
# CREATE_NO_WINDOW (no console); the working folder is the work folder, so stray files never land in the project
if (-not [HiddenDesk]::CreateProcessW($java, $cmd, [IntPtr]::Zero, [IntPtr]::Zero, $false, 0x08000000, [IntPtr]::Zero, $work, [ref]$si, [ref]$pi)) {
  throw "CreateProcess failed $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}
$pi.pid
