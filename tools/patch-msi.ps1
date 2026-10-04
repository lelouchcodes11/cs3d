# Patches the MSI that jpackage made so that an upgrade (and an uninstall) never deletes the user's data.
#
# Why: jpackage's installer deletes the whole install folder when a product is removed (WixRemoveFolderEx on INSTALLDIR). A major upgrade first
# removes the older version, with the OLDER version's own installer logic, so the folder is wiped including the data folder next to the exe where an
# installed copy keeps its settings, accounts and extensions when it has one (observed: an upgrade from 0.1.3 deleted 826 MB of data). The older
# versions cannot be changed, so this new installer protects the data itself:
#   1. before the older version is removed (sequence 797) the data folder of the older install is moved to %LOCALAPPDATA%\CloudStream-data-keep,
#      and Chromium helper processes that an earlier run left behind (parent gone) are stopped: they hold files of the data folder open;
#   2. after InstallFinalize the folder is moved back into the new install folder;
#   3. the new product no longer deletes the install folder as a whole when it is removed or replaced later: its WixRemoveFolderEx rows now point at
#      the app and runtime folders only (CS_RM_APP, CS_RM_RUNTIME), which is what removes the program files (every component is keyed on a registry value,
#      jpackage's installer deletes the files through that folder wipe).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-msi.ps1 <file.msi>
param([Parameter(Mandatory = $true)][string]$Msi)
$ErrorActionPreference = 'Stop'
$Msi = (Resolve-Path $Msi).Path

$installer = New-Object -ComObject WindowsInstaller.Installer
function Call($obj, $name, $kind, [object[]]$arguments) { $obj.GetType().InvokeMember($name, $kind, $null, $obj, $arguments) }
$db = Call $installer 'OpenDatabase' 'InvokeMethod' @($Msi, 1)   # 1 = msiOpenDatabaseModeTransact

function Run-Sql($sql, $record = $null) {
    $view = Call $db 'OpenView' 'InvokeMethod' @($sql)
    if ($record) { Call $view 'Execute' 'InvokeMethod' @($record) | Out-Null } else { Call $view 'Execute' 'InvokeMethod' $null | Out-Null }
    Call $view 'Close' 'InvokeMethod' $null | Out-Null
}
function New-Record($values) {
    $record = Call $installer 'CreateRecord' 'InvokeMethod' @([int]$values.Count)
    for ($i = 0; $i -lt $values.Count; $i++) {
        $v = $values[$i]
        if ($v -is [int]) { Call $record 'IntegerData' 'SetProperty' @([int]($i + 1), $v) | Out-Null }
        elseif ($null -ne $v) { Call $record 'StringData' 'SetProperty' @([int]($i + 1), [string]$v) | Out-Null }
    }
    $record
}
function Select-Rows($sql, $columns) {
    $view = Call $db 'OpenView' 'InvokeMethod' @($sql)
    Call $view 'Execute' 'InvokeMethod' $null | Out-Null
    while ($true) {
        $r = Call $view 'Fetch' 'InvokeMethod' $null
        if (-not $r) { break }
        (1..$columns | ForEach-Object { Call $r 'StringData' 'GetProperty' @([int]$_) }) -join ' | '
    }
    Call $view 'Close' 'InvokeMethod' $null | Out-Null
}

# no square brackets in the scripts except the two Windows Installer properties (they are formatted by the installer), and no double quotes
$keepScript = @'
$code = ('[JP_UPGRADABLE_FOUND]' -split ';' | Select-Object -First 1); if (-not $code) { exit 0 }; $d = $null; foreach ($prefix in 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\') { $k = Get-ItemProperty -Path ($prefix + $code) -ErrorAction SilentlyContinue; if ($k -and $k.InstallLocation) { $d = $k.InstallLocation; break } }; if (-not $d) { $d = $env:LOCALAPPDATA + '\CloudStream\' }; $data = Join-Path $d 'data'; $keep = Join-Path $env:LOCALAPPDATA 'CloudStream-data-keep'; Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'jcef_helper.exe' -and $_.ExecutablePath -like ($d + '*') -and -not (Get-Process -Id $_.ParentProcessId -ErrorAction SilentlyContinue) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; if ((Test-Path -LiteralPath $data) -and -not (Test-Path -LiteralPath $keep)) { Move-Item -LiteralPath $data -Destination $keep -ErrorAction SilentlyContinue }
'@
$restoreScript = @'
$data = '[INSTALLDIR]' + 'data'; $keep = $env:LOCALAPPDATA + '\CloudStream-data-keep'; if ((Test-Path -LiteralPath $keep) -and -not (Test-Path -LiteralPath $data)) { Move-Item -LiteralPath $keep -Destination $data }
'@
function As-Command($script) { '-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -Command "' + ($script.Trim() -replace '\s*\r?\n\s*', ' ') + '"' }

# (action, type, source, target); 51 = set a property, 114 = run an exe whose path is in a property (50) and go on if it fails (64)
$actions = @(
    @('CsSetPowerShell', 51, 'CS_POWERSHELL', '[SystemFolder]WindowsPowerShell\v1.0\powershell.exe'),
    @('CsKeepData', 114, 'CS_POWERSHELL', (As-Command $keepScript)),
    @('CsRestoreData', 114, 'CS_POWERSHELL', (As-Command $restoreScript))
)
foreach ($a in $actions) {
    Run-Sql ('DELETE FROM `CustomAction` WHERE `Action` = ''' + $a[0] + '''')
    Run-Sql 'INSERT INTO `CustomAction` (`Action`, `Type`, `Source`, `Target`) VALUES (?, ?, ?, ?)' (New-Record $a)
}
# (action, condition, sequence): the older version is removed at 798; the data goes aside just before that and comes back after InstallFinalize (6600)
$sequence = @(
    @('CsSetPowerShell', 'NOT Installed', 796),
    @('CsKeepData', 'NOT Installed', 797),
    @('CsRestoreData', 'NOT Installed AND NOT REMOVE', 6650)
)
foreach ($s in $sequence) {
    Run-Sql ('DELETE FROM `InstallExecuteSequence` WHERE `Action` = ''' + $s[0] + '''')
    Run-Sql 'INSERT INTO `InstallExecuteSequence` (`Action`, `Condition`, `Sequence`) VALUES (?, ?, ?)' (New-Record $s)
}
# This product keeps removing its program folders (app, runtime) when it is removed or replaced: with every component keyed on a registry value
# that wipe is how jpackage's installer deletes the files. But no longer the install folder as a whole, which holds the user's data.
# The folder to wipe is a property: the install folder that AppSearch read from the registry (RM_RF...), then \app and \runtime added.
$rmProperty = (Select-Rows 'SELECT `Property` FROM `AppSearch`' 1 | Where-Object { $_ -like 'RM_RF*' } | Select-Object -First 1)
$component = (Select-Rows 'SELECT `Component` FROM `Component`' 1 | Where-Object { $_ -like 'crm_rf*' } | Select-Object -First 1)
if (-not $rmProperty -or -not $component) { throw 'the install folder cleanup of jpackage was not found in the MSI' }
$more = @(
    @('CsSetRmApp', 51, 'CS_RM_APP', "[$rmProperty]app"),
    @('CsSetRmRuntime', 51, 'CS_RM_RUNTIME', "[$rmProperty]runtime")
)
foreach ($a in $more) {
    Run-Sql ('DELETE FROM `CustomAction` WHERE `Action` = ''' + $a[0] + '''')
    Run-Sql 'INSERT INTO `CustomAction` (`Action`, `Type`, `Source`, `Target`) VALUES (?, ?, ?, ?)' (New-Record $a)
}
# only when the install folder is known, so that "app" can never mean a folder relative to somewhere else; both run before the wipe (799)
foreach ($s in @(@('CsSetRmApp', $rmProperty, 790), @('CsSetRmRuntime', $rmProperty, 791))) {
    Run-Sql ('DELETE FROM `InstallExecuteSequence` WHERE `Action` = ''' + $s[0] + '''')
    Run-Sql 'INSERT INTO `InstallExecuteSequence` (`Action`, `Condition`, `Sequence`) VALUES (?, ?, ?)' (New-Record $s)
}
Run-Sql 'DELETE FROM `WixRemoveFolderEx`'
Run-Sql 'INSERT INTO `WixRemoveFolderEx` (`WixRemoveFolderEx`, `Component_`, `Property`, `InstallMode`) VALUES (?, ?, ?, ?)' (New-Record @('wrfCsApp', $component, 'CS_RM_APP', 2))
Run-Sql 'INSERT INTO `WixRemoveFolderEx` (`WixRemoveFolderEx`, `Component_`, `Property`, `InstallMode`) VALUES (?, ?, ?, ?)' (New-Record @('wrfCsRuntime', $component, 'CS_RM_RUNTIME', 2))

Call $db 'Commit' 'InvokeMethod' $null | Out-Null

# read back what the file contains now
$check = Select-Rows 'SELECT `Sequence`, `Action`, `Condition` FROM `InstallExecuteSequence`' 3 | Where-Object { $_ -match 'Cs(SetPowerShell|KeepData|RestoreData)|RemoveExistingProducts' }
$wipes = @(Select-Rows 'SELECT `WixRemoveFolderEx`, `Property` FROM `WixRemoveFolderEx`' 2)
$check | ForEach-Object { "patched: $_" }
$wipes | ForEach-Object { "patched: folder wipe row $_" }
$onlyPrograms = ($wipes.Count -eq 2) -and (($wipes | Where-Object { $_ -match 'CS_RM_APP|CS_RM_RUNTIME' }).Count -eq 2)
if (($check | Where-Object { $_ -match 'CsKeepData' }).Count -ne 1 -or ($check | Where-Object { $_ -match 'CsRestoreData' }).Count -ne 1 -or -not $onlyPrograms) { throw 'MSI patch did not apply' }
