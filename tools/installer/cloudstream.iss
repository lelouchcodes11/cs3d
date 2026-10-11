; Inno Setup script: a per-user installer that does not use Windows Installer (msiexec).
; Build: ISCC.exe /DAppVersion=1.0.2 /DPayload="<dir of the app image>" /DOutDir="<out dir>" tools\installer\cloudstream.iss
; The payload is the same image the MSI is made from (app\build\installer\CloudStream after ./gradlew :app:installerMsi).
; Data stays in %APPDATA%\CloudStream: it is never part of the payload and never removed by the uninstaller.

#ifndef AppVersion
  #define AppVersion "1.0.2"
#endif
#ifndef Payload
  #define Payload "..\..\app\build\installer\CloudStream"
#endif
#ifndef OutDir
  #define OutDir "..\..\dist\release"
#endif

[Setup]
AppId={{F3625499-8B66-4BA5-BBDE-814971321691}
AppName=CloudStream for Windows
AppVersion={#AppVersion}
AppVerName=CloudStream for Windows {#AppVersion}
AppPublisher=CloudStream for Windows
AppPublisherURL=https://github.com/lelouchcodes11/cs3d
AppSupportURL=https://discord.gg/u82JU5JDM
AppUpdatesURL=https://github.com/lelouchcodes11/cs3d/releases
VersionInfoVersion={#AppVersion}.0
DefaultDirName={autopf}\CloudStream
DefaultGroupName=CloudStream
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutDir}
OutputBaseFilename=CloudStream-{#AppVersion}-windows-x64-setup
SetupIconFile=..\..\app\src\main\icons\icon.ico
UninstallDisplayIcon={app}\CloudStream.exe
UninstallDisplayName=CloudStream for Windows
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
Name: "{autoprograms}\CloudStream"; Filename: "{app}\CloudStream.exe"
Name: "{autodesktop}\CloudStream"; Filename: "{app}\CloudStream.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\CloudStream.exe"; Description: "Start CloudStream"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; only the program: the settings live in %APPDATA%\CloudStream and are not touched
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
