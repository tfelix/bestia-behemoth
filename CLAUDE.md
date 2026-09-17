# bestia-behemoth

Kotlin/Spring Boot MMORPG monorepo: `login-server` (auth) + `zone-server` (game world,
Netty socket + ECS) talk to a Godot/C# client (`bestia-client`) over protobuf
(`bnet-messages`). World generation by `worldgen`. No shared DB between servers.
Shared code between servers, mostly login and inter-server communication related lives in `shared`.

For message flow, protobuf/envelope routing, the ECS game loop, or the login↔zone
handoff, read `.claude/skills/architecture/SKILL.md` first instead of re-deriving it.

## Running tests

`./gradlew :zone-server:test` is the default for day-to-day work. It compiles `worldgen` but does
not run its tests, so it costs ~3 minutes instead of ~25.

Run `./gradlew :worldgen:test` only when the change touches `worldgen/`, a `Stage`, a params class,
or worldgen configuration (`worldgen.*` in an `application.yml`). Run it as a separate invocation,
never together with `:zone-server:test` in one command, and never while another Gradle command is
running — the worldgen test executor runs out of memory under that load and reports the crash
against unrelated tests.

Anything that fans out to every module (`./gradlew test`, `check`, `build`, or a bare `./gradlew`
task at the root) includes the full worldgen suite. Use it before handing work over, not while
iterating.
