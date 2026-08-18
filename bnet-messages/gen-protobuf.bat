@echo off
REM Regenerates the C# protobuf classes the Godot client compiles against.
REM
REM %~dp0 is this script's own directory (bnet-messages\, with a trailing backslash) -
REM every path below is anchored to it so the script works regardless of the caller's
REM current directory or PATH, instead of relying on protoc.exe being found on PATH.
REM
REM Every .proto under the proto folder is compiled, found by walking the tree. This used to be a
REM hand-maintained list of eighty-odd explicit protoc lines, and the failure mode was silent and
REM nasty: a new .proto that was imported by envelope.proto but missing from the list produced an
REM Envelope.cs referencing a C# type that had never been generated, so the client failed to build
REM with an error pointing at a file nobody had touched. Walking the tree cannot drift.
REM
REM Nothing is excluded, and nothing was before either - the old list held all 85 protos that existed
REM when it was last correct.

setlocal
set SCRIPT_DIR=%~dp0
set PROTOC=%SCRIPT_DIR%protoc.exe

REM Define the folder containing the .proto files
set PROTO_FOLDER=%SCRIPT_DIR%src\main\proto

REM Define the output folder for the generated C# files
set OUTPUT_FOLDER=%SCRIPT_DIR%..\bestia-client\src\Bnet\Proto

REM Clear the output folder if it exists, so a deleted .proto does not leave its C# behind
if exist "%OUTPUT_FOLDER%" (
    echo Clearing output folder...
    rmdir /s /q "%OUTPUT_FOLDER%"
)

if not exist "%OUTPUT_FOLDER%" mkdir "%OUTPUT_FOLDER%"

set COUNT=0
set FAILED=0

for /r "%PROTO_FOLDER%" %%f in (*.proto) do (
    "%PROTOC%" --proto_path="%PROTO_FOLDER%" --csharp_out="%OUTPUT_FOLDER%" "%%f"
    if errorlevel 1 (
        echo FAILED: %%f
        set /a FAILED+=1
    ) else (
        set /a COUNT+=1
    )
)

echo Compiled %COUNT% proto file^(s^).

if %FAILED% GTR 0 (
    echo %FAILED% proto file^(s^) failed to compile.
    exit /b 1
)

echo Protobuf compilation complete.
