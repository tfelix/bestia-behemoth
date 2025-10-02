@echo off
REM Define the relative folder containing the .proto files
set PROTO_FOLDER=..\bnet-messages\src\main\proto

REM Define the relative output folder for the generated C# files
set OUTPUT_FOLDER=..\bestia-client\src\Bnet\Proto

REM Clear the output folder if it exists
if exist "%OUTPUT_FOLDER%" (
    echo Clearing output folder...
    rmdir /s /q "%OUTPUT_FOLDER%"
)

REM Ensure the output folder exists
if not exist "%OUTPUT_FOLDER%" mkdir "%OUTPUT_FOLDER%"

REM Run the protoc command to generate C# files
REM
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\envelope.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\account.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\vec3.proto"

REM System messages
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\authentication.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\authentication_success.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\operation_success.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\operation_error.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\ping.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\chat_cmsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\chat_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\disconnected.proto"

REM Component messages
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\position_component.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\bestia_visual_component.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\master_visual_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\path_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\speed_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\item_visual_component.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\level_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\exp_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\health_component_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\mana_component_smsg.proto"

REM Entity messages
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\attack_entity_cmsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\move_active_entity.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\select_active_entity.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\get_all_entities.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\vanish_entity_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\damage_entity_smsg.proto"

REM Master messages
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\bestia_info.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\get_master_cmsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\get_self_cmsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\self_smsg.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\master.proto"
protoc.exe --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\select_master_cmsg.proto"

echo Protobuf compilation complete.
pause