# Patches the MSI that jpackage made so that an upgrade (and an uninstall) never deletes the user's data.
#
# Why: jpackage's installer deletes the whole install folder when a product is removed (WixRemoveFolderEx on INSTALLDIR). A major upgrade first
# removes the older version, with the OLDER version's own installer logic, so the folder is wiped including a data folder next to the exe (an
# installed copy keeps its settings, accounts and extensions in %APPDATA%\CloudStream, unless a `data` folder sits next to the exe: that was the
# case of the 0.1.3 / 0.1.4 portable-like installs, an upgrade from 0.1.3 deleted 826 MB of data). This installer no longer deletes the install
# folder as a whole when it is removed or replaced later: its WixRemoveFolderEx rows point at the app and runtime folders only (CS_RM_APP,
# CS_RM_RUNTIME), which is what removes the program files (every component is keyed on a registry value, jpackage's installer deletes the files
# through that folder wipe).
#
# Since 1.0.3 nothing in the MSI runs a script. Earlier versions of this patch added three PowerShell custom actions (move the data folder of an
# older install aside and back, with -ExecutionPolicy Bypass -WindowStyle Hidden): a console window flashed during the installation and users
# found it, rightly, shady. They only protected the `data` folder of 0.1.3 / 0.1.4 installs, which the 0.1.5+ installers never create.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\patch-msi.ps1 <file.msi>   (build time only; the MSI itself runs no script)
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

# an MSI of an earlier patch (or one patched twice) must not keep the script actions
foreach ($old in @('CsSetPowerShell', 'CsKeepData', 'CsRestoreData')) {
    Run-Sql ('DELETE FROM `InstallExecuteSequence` WHERE `Action` = ''' + $old + '''')
    Run-Sql ('DELETE FROM `CustomAction` WHERE `Action` = ''' + $old + '''')
}

# This product keeps removing its program folders (app, runtime) when it is removed or replaced: with every component keyed on a registry value
# that wipe is how jpackage's installer deletes the files. But no longer the install folder as a whole, which may hold the user's data.
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
$wipes = @(Select-Rows 'SELECT `WixRemoveFolderEx`, `Property` FROM `WixRemoveFolderEx`' 2)
$actions = @(Select-Rows 'SELECT `Action`, `Type`, `Source` FROM `CustomAction`' 3)
$wipes | ForEach-Object { "patched: folder wipe row $_" }
$onlyPrograms = ($wipes.Count -eq 2) -and (($wipes | Where-Object { $_ -match 'CS_RM_APP|CS_RM_RUNTIME' }).Count -eq 2)
# nothing in the installer may start a script interpreter or a shell
$scripts = @($actions | Where-Object { $_ -match 'powershell|cmd\.exe|wscript|cscript|\.vbs|\.ps1' -or ($_ -split ' \| ')[2] -match 'POWERSHELL' })
$targets = @(Select-Rows 'SELECT `Target` FROM `CustomAction`' 1 | Where-Object { $_ -match 'powershell|cmd\.exe|wscript|cscript|-ExecutionPolicy' })
if (-not $onlyPrograms) { throw 'MSI patch did not apply (folder wipe rows)' }
if ($scripts.Count -gt 0 -or $targets.Count -gt 0) { throw ('the MSI still runs a script: ' + (($scripts + $targets) -join '; ')) }
'patched: no custom action runs a script or a shell'
