#!/usr/bin/env bash
# Regenerates the C# protobuf classes the Godot client compiles against.
# Linux/macOS twin of gen-protobuf.bat - keep both in sync when either changes.
#
# Every path is anchored to this script's own directory, so it works regardless of the
# caller's working directory and does not rely on a protoc on PATH.
#
# Every .proto under the proto folder is compiled, found by walking the tree. This used to be a
# hand-maintained list of eighty-odd explicit protoc lines, and the failure mode was silent and
# nasty: a new .proto that was imported by envelope.proto but missing from the list produced an
# Envelope.cs referencing a C# type that had never been generated, so the client failed to build
# with an error pointing at a file nobody had touched. Walking the tree cannot drift.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTOC="$SCRIPT_DIR/protoc"
PROTO_FOLDER="$SCRIPT_DIR/src/main/proto"
OUTPUT_FOLDER="$SCRIPT_DIR/../bestia-client/src/Bnet/Proto"

# The vendored protoc is a Linux x86-64 binary; on any other platform fall back to PATH so the
# script still works there, as long as the versions match (see --version below).
if ! "$PROTOC" --version >/dev/null 2>&1; then
    if ! command -v protoc >/dev/null 2>&1; then
        echo "Cannot run $PROTOC and no protoc on PATH - on Windows use gen-protobuf.bat instead." >&2
        exit 1
    fi
    PROTOC="$(command -v protoc)"
    echo "Vendored protoc is not runnable here, using $PROTOC ($("$PROTOC" --version))."
fi

# Clear the output folder, so a deleted .proto does not leave its C# behind
rm -rf "$OUTPUT_FOLDER"
mkdir -p "$OUTPUT_FOLDER"

mapfile -t PROTOS < <(find "$PROTO_FOLDER" -name '*.proto' | sort)
"$PROTOC" --proto_path="$PROTO_FOLDER" --csharp_out="$OUTPUT_FOLDER" "${PROTOS[@]}"

echo "Compiled ${#PROTOS[@]} proto file(s)."
