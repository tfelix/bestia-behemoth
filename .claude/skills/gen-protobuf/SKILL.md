---
name: gen-protobuf
description: Regenerate the C# protobuf client files after editing any .proto file in bnet-messages/src/main/proto/. Use this whenever a .proto message definition changed, before committing — otherwise the Godot client (bestia-client) keeps stale generated code that no longer matches the schema. Triggers on: .proto, protobuf, protoc, Envelope, CMSG, SMSG, bnet-messages, regenerate proto, gen-protobuf.
---

# Regenerate Protobuf Messages

Run this skill after modifying any `.proto` file in `bnet-messages/src/main/proto/` to regenerate the C# files used by the Godot client.

## How to run

Open a terminal (Powershell), navigate into the `bnet-messages` directory, and execute the batch file:

```
cd bnet-messages
.\gen-protobuf.bat
```

`protoc.exe` lives in `bnet-messages/` alongside the script — it must be run from that directory so the executable is found.

## What it does

- Clears `bestia-client/src/Bnet/Proto/`
- Runs `protoc.exe` on every `.proto` file
- Writes the regenerated C# classes back to `bestia-client/src/Bnet/Proto/`

## Important notes

- The Kotlin/JVM classes (used by the zone-server) are generated at build time by Gradle via the `com.google.protobuf` plugin — no manual step needed there.
- The C# output files are committed to the repo; always regenerate and commit them together with any `.proto` change.
- After regenerating, verify the expected types exist in the output (e.g. `grep COMMAND bestia-client/src/Bnet/Proto/ChatCmsg.cs`).
