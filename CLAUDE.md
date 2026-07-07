# bestia-behemoth

Kotlin/Spring Boot MMORPG monorepo: `login-server` (auth) + `zone-server` (game world,
Netty socket + ECS) talk to a Godot/C# client (`bestia-client`) over protobuf
(`bnet-messages`). No shared DB between servers — only a JWT.

For message flow, protobuf/Envelope routing, the ECS game loop, or the login↔zone
handoff, read `.claude/skills/architecture/SKILL.md` first instead of re-deriving it.
