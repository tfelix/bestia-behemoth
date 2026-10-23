# bestia-behemoth

Kotlin/Spring Boot MMORPG monorepo: `login-server` (auth) + `zone-server` (game world,
Netty socket + ECS) talk to a Godot/C# client (`bestia-client`) over protobuf
(`bnet-messages`). World generation by `worldgen`. No shared DB between servers.
Shared code between servers, mostly login and inter-server communication related lives in `shared`.
The companion web app (`companion-app/`, SvelteKit, npm) talks JSON to `api-server`, which also serves the game
client's map and asks the zone over the Valkey bus in `bus` (the zone owns Valkey: `zone-server/compose.yaml`).
The login server kicks players over the same bus. The zone runs no web server: it listens on its game socket and a
loopback metrics port only.

For message flow, protobuf/envelope routing, the ECS game loop, or the login↔zone
handoff, read `.claude/skills/architecture/SKILL.md` first instead of re-deriving it.

## Running tests

`./gradlew :zone-server:test` is the default for day-to-day work. It compiles `worldgen` but does
not run its tests, so it costs ~3 minutes instead of ~8.

Run `./gradlew :worldgen:test` only when the change touches `worldgen/`, a `Stage`, a params class,
or worldgen configuration (`worldgen.*` in an `application.yml`). It takes ~5 minutes and uses most of
the machine: four test classes run at once on a 6 GB heap. Run it as a separate invocation,
never together with `:zone-server:test` in one command, and never while another Gradle command is
running — the worldgen test executor runs out of memory under that load and reports the crash
against unrelated tests. A test that needs a generated world asks `TestWorlds.get(...)` rather than
calling `StandardWorld.build`; `.claude/skills/worldgen/SKILL.md` says why and when not to.

`./gradlew :bus:test` and `:api-server:test` need Docker (Testcontainers runs Valkey and MariaDB).
`./gradlew companionCheck` type-checks and unit-tests the companion app; it needs Node 22+. After
changing a REST DTO, regenerate the app's types: `:api-server:test --tests
net.bestia.api.OpenApiDocumentTest`, then `npm run gen:api` in `companion-app/`.

Anything that fans out to every module (`./gradlew test`, `check`, `build`, or a bare `./gradlew`
task at the root) includes the full worldgen suite. Use it before handing work over, not while
iterating.
