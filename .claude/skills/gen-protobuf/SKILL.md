---
name: gen-protobuf
description: Regenerate the C# protobuf client files after editing any .proto file in bnet-messages/src/main/proto/. Use this whenever a .proto message definition changed, before committing — otherwise the Godot client (bestia-client) keeps stale generated code that no longer matches the schema. Triggers on: .proto, protobuf, protoc, Envelope, CMSG, SMSG, bnet-messages, regenerate proto, gen-protobuf.
---

# Regenerate Protobuf Messages

Run this skill after modifying any `.proto` file in `bnet-messages/src/main/proto/` to regenerate the C# files used by the Godot client.

## How to run

Pick the script for the platform you are on — both do the same thing, and both run
from anywhere, they do not need `bnet-messages/` to be the current directory:

| Platform | Script | Compiler it uses |
| --- | --- | --- |
| Linux, macOS | `bnet-messages/gen-protobuf.sh` | `bnet-messages/protoc` |
| Windows | `bnet-messages/gen-protobuf.bat` | `bnet-messages/protoc.exe` |

Both compilers are vendored in `bnet-messages/` at the same version, so either script
produces the same output. Each script resolves its compiler and every proto/output path
from its own location, so neither relies on a `protoc` on `PATH` or on the caller's
working directory. The `.sh` falls back to a `protoc` on `PATH` where the vendored Linux
binary cannot run (macOS) — that one has to match the vendored version.

## What it does

- Clears `bestia-client/src/Bnet/Proto/`
- Walks `bnet-messages/src/main/proto/` and compiles every `.proto` it finds
- Writes the regenerated C# classes back to `bestia-client/src/Bnet/Proto/`

## Important notes

- **A brand-new `.proto` needs no change to either script.** They walk the proto tree, so
  a new file is picked up automatically. This used to be a hand-maintained list of explicit
  `protoc` lines with exactly the silent failure you would fear — a new proto imported by
  `envelope.proto` but missing from the list produced an `Envelope.cs` referencing a C# type
  that had never been generated — and the scripts' own headers record why that was replaced.
- **Change one script, change the other.** They are twins; drift between them means the
  output depends on who regenerated it.
- The Kotlin/JVM classes (used by the zone-server) are generated at build time by Gradle via the `com.google.protobuf` plugin — no manual step needed there.
- The C# output files are committed to the repo; always regenerate and commit them together with any `.proto` change.
- After regenerating, verify the expected types exist in the output (e.g. `grep COMMAND bestia-client/src/Bnet/Proto/ChatCmsg.cs`).
