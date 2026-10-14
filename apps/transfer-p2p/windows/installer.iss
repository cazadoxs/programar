#define AppVer GetEnv("PASALO_VERSION")
#if AppVer == ""
  #define AppVer "1.0.0"
#endif

[Setup]
AppId={{6F3B1A52-8C1E-4F7A-9D2C-5A50A1C0DE01}
AppName=Pásalo
AppVersion={#AppVer}
AppPublisher=cazadoxs
DefaultDirName={autopf}\Pasalo
DefaultGroupName=Pásalo
DisableProgramGroupPage=yes
OutputDir=..\build\installer
OutputBaseFilename=Pasalo-Setup
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
SetupIconFile=runner\resources\app_icon.ico
UninstallDisplayIcon={app}\pasalo.exe
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Pásalo"; Filename: "{app}\pasalo.exe"
Name: "{autodesktop}\Pásalo"; Filename: "{app}\pasalo.exe"; Tasks: desktopicon

[Registry]
; Enlaces pasalo://
Root: HKCU; Subkey: "Software\Classes\pasalo"; ValueType: string; ValueData: "URL:Pásalo"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\pasalo"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKCU; Subkey: "Software\Classes\pasalo\DefaultIcon"; ValueType: string; ValueData: "{app}\pasalo.exe,0"
Root: HKCU; Subkey: "Software\Classes\pasalo\shell\open\command"; ValueType: string; ValueData: """{app}\pasalo.exe"" ""%1"""

[Run]
Filename: "{app}\pasalo.exe"; Description: "{cm:LaunchProgram,Pásalo}"; Flags: nowait postinstall skipifsilent
