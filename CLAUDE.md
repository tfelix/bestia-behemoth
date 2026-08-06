# bestia-behemoth

Kotlin/Spring Boot MMORPG monorepo: `login-server` (auth) + `zone-server` (game world,
Netty socket + ECS) talk to a Godot/C# client (`bestia-client`) over protobuf
(`bnet-messages`). World generation by `worldgen`. No shared DB between servers. Shared code between servers, mostly
login and inter-server communication related lives in `shared`.

For message flow, protobuf/envelope routing, the ECS game loop, or the login↔zone
handoff, read `.claude/skills/architecture/SKILL.md` first instead of re-deriving it.
