; Inno Setup script: a per-user installer that does not use Windows Installer to install (msiexec is only used to REMOVE an earlier MSI copy).
; Build: ISCC.exe /DAppVersion=1.0.4 /DPayload="<dir of the app image>" /DOutDir="<out dir>" tools\installer\cloudstream.iss
; The payload is the same image the MSI is made from (app\build\installer\CloudStream after ./gradlew :app:installerMsi).
; Data stays in %APPDATA%\CloudStream: it is never part of the payload and never removed by the uninstaller.
;
; UPDATING AN MSI COPY. Many people installed from the MSI. This setup finds that copy (Windows Installer's own answer for the upgrade code
; of the MSI, so a copy in any folder and any version since 0.1.0 is found), closes the running app, removes the MSI product and installs
; into the SAME folder, so pins, shortcuts and the folder the person chose stay. Settings, accounts, extensions and history live in
; %APPDATA%\CloudStream and are never touched (a `data` folder next to the exe, the 0.1.3 / 0.1.4 layout, is moved there first: the MSI's
; own removal would delete it). From then on the app is a "Setup" install and its updater downloads the -setup.exe of the releases.
; Without this the setup would have put a second copy beside the MSI one (two entries in Apps, two shortcuts of the same name).
;
; Test knobs (all have the production value as default; a test run points them at a throwaway MSI product and folders):
;   /DAppIdValue=  /DAppNameValue=  /DMsiUpgradeCode=  /DAppExe=  /DShortcutName=  /DLegacyDataTarget=

#ifndef AppVersion
  #define AppVersion "1.0.4"
#endif
#ifndef Payload
  #define Payload "..\..\app\build\installer\CloudStream"
#endif
#ifndef OutDir
  #define OutDir "..\..\dist\release"
#endif
#ifndef AppIdValue
  #define AppIdValue "{{F3625499-8B66-4BA5-BBDE-814971321691}"
#endif
#ifndef AppNameValue
  #define AppNameValue "CloudStream for Windows"
#endif
; the UpgradeCode jpackage was given in app/build.gradle.kts (--win-upgrade-uuid): the same in every MSI of the project
#ifndef MsiUpgradeCode
  #define MsiUpgradeCode "{8F7A1C2E-8D5A-4B1E-9F0E-6F1C9D3A2B7C}"
#endif
#ifndef AppExe
  #define AppExe "CloudStream.exe"
#endif
#ifndef ShortcutName
  #define ShortcutName "CloudStream"
#endif
#ifndef LegacyDataTarget
  #define LegacyDataTarget "{userappdata}\CloudStream"
#endif

[Setup]
AppId={#AppIdValue}
AppName={#AppNameValue}
AppVersion={#AppVersion}
AppVerName={#AppNameValue} {#AppVersion}
AppPublisher=CloudStream for Windows
AppPublisherURL=https://github.com/lelouchcodes11/cs3d
AppSupportURL=https://discord.gg/u82JU5JDM
AppUpdatesURL=https://github.com/lelouchcodes11/cs3d/releases
VersionInfoVersion={#AppVersion}.0
; the folder of an MSI copy when there is one (see [Code]), else %LOCALAPPDATA%\Programs\CloudStream; a copy of this setup program is reused by
; Inno itself (UsePreviousAppDir)
DefaultDirName={code:DefaultAppDir}
DefaultGroupName=CloudStream
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutDir}
OutputBaseFilename=CloudStream-{#AppVersion}-windows-x64-setup
SetupIconFile=..\..\app\src\main\icons\icon.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppNameValue}
Compression=lzma2/normal
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "{#Payload}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#ShortcutName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#ShortcutName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "Start CloudStream"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; only the program: the settings live in %APPDATA%\CloudStream and are not touched
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Code]
var
  { the MSI copy found when Setup started: its product codes, version and folder (empty when there is none) }
  MsiCodes: TArrayOfString;
  MsiVersion, MsiDir: String;

{ ---- Windows Installer: is there an MSI copy of this app? ---------------------------------------------------------------------------- }

{ Asks Windows Installer for the products that carry the upgrade code (per-user and per-machine ones are both answered; the product of any
  version of the project has it). Version and folder are those of the first product whose folder exists (else of the first product); a
  product without a recorded folder is taken to be in the default folder of the MSI. Changes no global. }
procedure FindMsiCopies(var Codes: TArrayOfString; var Version, Dir: String);
var
  Installer, Related: Variant;
  I, N: Integer;
  Code, Where, Ver: String;
  Chosen: Boolean;
begin
  SetArrayLength(Codes, 0);
  Version := '';
  Dir := '';
  Chosen := False;
  try
    Installer := CreateOleObject('WindowsInstaller.Installer');
    Related := Installer.RelatedProducts('{#MsiUpgradeCode}');
    N := Related.Count;
    for I := 0 to N - 1 do
    begin
      Code := Related.Item(I);
      Where := RemoveBackslash(Installer.ProductInfo(Code, 'InstallLocation'));
      if Where = '' then Where := ExpandConstant('{localappdata}\CloudStream');
      Ver := Installer.ProductInfo(Code, 'VersionString');
      SetArrayLength(Codes, GetArrayLength(Codes) + 1);
      Codes[GetArrayLength(Codes) - 1] := Code;
      if (not Chosen) and ((Dir = '') or DirExists(Where)) then
      begin
        Dir := Where;
        Version := Ver;
        Chosen := DirExists(Where);
      end;
    end;
  except
    Log('Windows Installer query failed: ' + GetExceptionMessage);
  end;
end;

{ -1, 0, 1 for dotted numbers ("1.0.10" is newer than "1.0.9") }
function CompareVersions(A, B: String): Integer;
var
  PA, PB: Integer;
  VA, VB: Integer;
begin
  Result := 0;
  while (Result = 0) and ((A <> '') or (B <> '')) do
  begin
    PA := Pos('.', A);
    if PA = 0 then PA := Length(A) + 1;
    PB := Pos('.', B);
    if PB = 0 then PB := Length(B) + 1;
    VA := StrToIntDef(Copy(A, 1, PA - 1), 0);
    VB := StrToIntDef(Copy(B, 1, PB - 1), 0);
    if VA < VB then Result := -1 else if VA > VB then Result := 1;
    Delete(A, 1, PA);
    Delete(B, 1, PB);
  end;
end;

function DefaultAppDir(Param: String): String;
begin
  if MsiDir <> '' then Result := MsiDir
  else Result := ExpandConstant('{autopf}\CloudStream');
end;

function InitializeSetup: Boolean;
begin
  FindMsiCopies(MsiCodes, MsiVersion, MsiDir);
  if GetArrayLength(MsiCodes) > 0 then Log('MSI copy found: ' + MsiVersion + ' in ' + MsiDir);
  Result := True;
end;

{ ---- the running app --------------------------------------------------------------------------------------------------------------- }

{ WQL strings escape the backslash }
function WqlString(S: String): String;
begin
  StringChangeEx(S, '\', '\\', True);
  StringChangeEx(S, '''', '\''', True);
  Result := S;
end;

{ How many processes run this exact program file; Action 1: the windows of each are asked to close (taskkill without /F: the app saves and
  ends the normal way), 2: they are ended. Only the copy at [ExePath] is looked at: a portable copy or a development run of the app
  elsewhere on the PC has the same name and is left alone. }
function AppProcesses(ExePath: String; Action: Integer): Integer;
var
  Locator, Wmi, List: Variant;
  I, Pid, Code: Integer;
  Flags: String;
begin
  Result := 0;
  try
    Locator := CreateOleObject('WbemScripting.SWbemLocator');
    Wmi := Locator.ConnectServer('.', 'root\CIMV2');
    List := Wmi.ExecQuery('SELECT ProcessId FROM Win32_Process WHERE ExecutablePath = ''' + WqlString(ExePath) + '''');
    Result := List.Count;
    if Action = 0 then Exit;
    if Action = 2 then Flags := ' /F /T' else Flags := '';
    for I := 0 to Result - 1 do
    begin
      Pid := List.ItemIndex(I).ProcessId;
      Exec(ExpandConstant('{sys}\taskkill.exe'), '/PID ' + IntToStr(Pid) + Flags, '', SW_HIDE, ewWaitUntilTerminated, Code);
    end;
  except
    Log('process query failed: ' + GetExceptionMessage);
  end;
end;

{ True when the app of [Dir] is not running (any more) }
function CloseApp(Dir: String): Boolean;
var
  Exe: String;
  I: Integer;
begin
  Exe := AddBackslash(Dir) + '{#AppExe}';
  Result := AppProcesses(Exe, 0) = 0;
  if Result then Exit;
  { the person is asked: the app is theirs and may be in the middle of something }
  if not WizardSilent then
    if MsgBox('CloudStream is running. Setup closes it now to go on with the update (your settings are saved first).' + #13#10 + #13#10 +
              'Close CloudStream and continue?', mbConfirmation, MB_YESNO) <> IDYES then
      Exit;
  AppProcesses(Exe, 1);
  for I := 1 to 40 do
  begin
    if AppProcesses(Exe, 0) = 0 then begin Result := True; Exit; end;
    { a window that was not there yet (the app is still starting) hears the request only now }
    if (I mod 8) = 0 then AppProcesses(Exe, 1);
    Sleep(500);
  end;
  { it did not close (hung, or it hides in the tray instead): ended }
  AppProcesses(Exe, 2);
  for I := 1 to 10 do
  begin
    if AppProcesses(Exe, 0) = 0 then begin Result := True; Exit; end;
    Sleep(500);
  end;
end;

{ ---- the data folder of the 0.1.3 / 0.1.4 layout ------------------------------------------------------------------------------------ }

{ A `data` folder next to the exe is where those versions kept everything, and the MSI's removal of a folder deletes it. It is moved to the
  settings folder of an installed copy (where the app looks for it when no `data` folder sits next to the exe); when that exists already it
  is only put aside, beside it, never deleted. False when it could not be moved: then nothing is removed. }
function KeepLegacyData(Dir: String): Boolean;
var
  Old, Target, Aside: String;
begin
  Result := True;
  Old := AddBackslash(Dir) + 'data';
  if not DirExists(Old) then Exit;
  Target := ExpandConstant('{#LegacyDataTarget}');
  if not DirExists(Target) then
  begin
    ForceDirectories(ExtractFileDir(Target));
    Result := RenameFile(Old, Target);
  end
  else
  begin
    Aside := Target + '-from-install-' + GetDateTimeString('yyyymmdd-hhnnss', '-', ':');
    Result := RenameFile(Old, Aside);
  end;
  if not Result then Log('could not move the data folder ' + Old);
end;

{ ---- removing the MSI copy ------------------------------------------------------------------------------------------------------------ }

{ 0 when the product is gone }
function RemoveMsiProduct(Code: String): Integer;
var
  Msi: String;
begin
  Msi := ExpandConstant('{sys}\msiexec.exe');
  Result := 0;
  if not Exec(Msi, '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Result) then
    Result := -1;
  { 1925 / 1730: a product installed for all users needs administrator rights: the same again with the Windows prompt }
  if (Result = 1925) or (Result = 1730) then
    if not ShellExec('runas', Msi, '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Result) then
      Result := -1;
  { 3010 / 1641: removed, a restart is wanted (nothing here needs it); 1605: it was not there }
  if (Result = 3010) or (Result = 1641) or (Result = 1605) then Result := 0;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  I, Code: Integer;
  Left: TArrayOfString;
  LeftVersion, LeftDir, Failed: String;
begin
  Result := '';
  if GetArrayLength(MsiCodes) = 0 then Exit;
  Failed := 'CloudStream ' + MsiVersion + ' (installed from the MSI) could not be replaced.' + #13#10 + #13#10 +
            'Your settings are untouched. Close CloudStream, remove "CloudStream" under Settings > Apps > Installed apps, ' +
            'then run this setup again.';

  if (MsiVersion <> '') and (CompareVersions(MsiVersion, '{#AppVersion}') > 0) then
  begin
    Result := 'A newer version of CloudStream (' + MsiVersion + ') is installed from the MSI. This setup is version {#AppVersion}. ' +
              'Use the setup program of that version or newer.';
    Exit;
  end;

  { (Setup's log stamps every line, which is how long each step took) }
  if DirExists(MsiDir) then
  begin
    if not CloseApp(MsiDir) then begin Result := Failed; Exit; end;
    Log('the app is closed');
    if not KeepLegacyData(MsiDir) then begin Result := Failed; Exit; end;
  end;

  for I := 0 to GetArrayLength(MsiCodes) - 1 do
  begin
    Code := RemoveMsiProduct(MsiCodes[I]);
    Log('MSI product ' + MsiCodes[I] + ' removed with code ' + IntToStr(Code));
  end;

  { what Windows Installer says now is the last word; a registration whose folder is gone for good must not block the update }
  FindMsiCopies(Left, LeftVersion, LeftDir);
  if (GetArrayLength(Left) > 0) and DirExists(LeftDir) then
    Result := Failed;
end;

{ The "Ready to Install" page says what is about to happen to the MSI copy }
function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo, MemoTypeInfo, MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
var
  S: String;
begin
  S := '';
  if GetArrayLength(MsiCodes) > 0 then
    S := 'CloudStream ' + MsiVersion + ' (installed from the MSI) was found in:' + NewLine + Space + MsiDir + NewLine +
         'Setup replaces it with this version in the same folder. Your settings, accounts and extensions are kept.' + NewLine + NewLine;
  S := S + MemoDirInfo;
  if MemoTasksInfo <> '' then S := S + NewLine + NewLine + MemoTasksInfo;
  Result := S;
end;
