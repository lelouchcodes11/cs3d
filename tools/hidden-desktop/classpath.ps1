# Writes the app's runtime classpath to build/hidden-desktop/classpath.txt (run after building, and again when dependencies change)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$work = Join-Path $root "build\hidden-desktop"
New-Item -ItemType Directory -Force $work | Out-Null
$init = Join-Path $work "classpath.gradle"
@'
allprojects { p ->
  p.afterEvaluate {
    if (p.path == ':app') {
      p.tasks.register('printHiddenDesktopClasspath') { doLast { new File(System.getProperty('cpout')).text = p.sourceSets.main.runtimeClasspath.asPath } }
    }
  }
}
'@ | Set-Content $init -Encoding ascii
& (Join-Path $root "gradlew.bat") -q -p $root -I $init :app:printHiddenDesktopClasspath "-Dcpout=$work\classpath.txt"
if ($LASTEXITCODE -ne 0) { throw "gradle failed" }
"classpath written: $work\classpath.txt"
