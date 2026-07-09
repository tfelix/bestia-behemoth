@echo off
REM %~dp0 is this script's own directory (bnet-messages\, with a trailing backslash) -
REM every path below is anchored to it so the script works regardless of the caller's
REM current directory or PATH, instead of relying on protoc.exe being found on PATH.
set SCRIPT_DIR=%~dp0
set PROTOC=%SCRIPT_DIR%protoc.exe

REM Define the folder containing the .proto files
set PROTO_FOLDER=%SCRIPT_DIR%src\main\proto

REM Define the output folder for the generated C# files
set OUTPUT_FOLDER=%SCRIPT_DIR%..\bestia-client\src\Bnet\Proto

REM Clear the output folder if it exists
if exist "%OUTPUT_FOLDER%" (
    echo Clearing output folder...
    rmdir /s /q "%OUTPUT_FOLDER%"
)

REM Ensure the output folder exists
if not exist "%OUTPUT_FOLDER%" mkdir "%OUTPUT_FOLDER%"

REM Run the protoc command to generate C# files
REM
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\envelope.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\account.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\vec3.proto"

REM System messages
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\authentication.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\authentication_success.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\operation_success.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\operation_error.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\ping.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\chat_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\chat_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\system\disconnected.proto"

REM Component messages
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\position_component.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\bestia_visual_component.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\master_visual_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\path_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\speed_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\item_visual_component.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\level_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\exp_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\health_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\mana_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\inventory_component_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\skill_points_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\component\skill_list_smsg.proto"

REM Entity messages
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\attack_entity_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\move_active_entity.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\select_active_entity.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\get_all_entities.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\vanish_entity_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\entity\damage_entity_smsg.proto"

REM Inventory messages
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\inventory\get_inventory_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\inventory\use_item_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\inventory\drop_item_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\inventory\loot_item_cmsg.proto"

REM Master messages
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\bestia_info.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\get_master_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\get_self_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\self_smsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\master.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\select_master_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\invest_skill_point_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\get_skills_cmsg.proto"
"%PROTOC%" --proto_path=%PROTO_FOLDER% --csharp_out=%OUTPUT_FOLDER% "%PROTO_FOLDER%\messages\master\activate_skill_cmsg.proto"

echo Protobuf compilation complete.