---
name: architecture
description: Explains the bestia-behemoth server architecture — protobuf message contracts (bnet-messages), the Envelope wire format, Netty socket handling, message dispatch/handlers, the ECS (entity/component/system) game loop and the parallel ecs2 rewrite, the login-server↔zone-server JWT handoff, and the AI module. Read this BEFORE investigating "how does message X travel from client to server", "where does system Y live", "how do I add a new message type", or any other cross-cutting networking/ECS question — it gives file:line pointers so the answer doesn't have to be re-derived from scratch. Triggers on: protobuf, .proto, Envelope, CMSG, SMSG, Netty, socket server, zone-server, login-server, ECS, ecs2, entity component system, message handler, JWT auth, bnet-messages, ChannelRegistry, World tick, gen-protobuf.
---

# bestia-behemoth server architecture

Kotlin/Spring Boot monorepo MMORPG. A Godot/C# client talks to two independent JVM
services over a hand-rolled binary protobuf protocol: **login-server** (stateless REST
auth) and **zone-server** (the actual game world, Netty socket + custom ECS tick loop).
There is no shared database between the two servers — only a signed JWT.

Official game-design docs (not much server-internals depth) live at
https://docs.bestia-game.net/docs/, source at github.com/tfelix/bestia-docs. For
architecture questions, this file and the source are more authoritative than the docs site.

## Repo layout

| Module | Role |
|---|---|
| `bestia-client` | Godot game client (C#) |
| `bnet-messages` | protobuf message contracts — the wire format shared by client & server |
| `cli-client` | headless Kotlin dev/test client (`cli-client/src/main/kotlin/net/bestia/client/CLI.kt`) — connects a raw socket + REST auth without needing Godot, useful for manually exercising zone-server |
| `login-server` | Spring Boot REST auth service, issues JWTs |
| `shared` | small Kotlin types shared by both servers: `Role`/`Authority` (`shared/src/main/kotlin/net/bestia/account/`), EIP712 auth DTOs. **Not** shared DB entities — each server defines its own JPA `Account` |
| `zone-server` | Spring Boot game server: Netty TCP socket, message dispatch, ECS game loop, AI |

Gradle multi-module build (`settings.gradle`); each server is its own Spring Boot app
with its own `application.yml`.

## Message contracts: bnet-messages

`bnet-messages/src/main/proto/` is the single source of truth for the wire protocol:

- `envelope.proto` defines one `Envelope` message with a big `oneof`, importing every
  leaf message. Field numbers are grouped into manually-maintained ranges per domain
  (`SYSTEM & ACCOUNT 100`, `MAP 200`, `INVENTORY 300`, `MASTER & BESTIA 400`,
  `ENTITY & COMPONENTS 500`) — when adding a message, follow the existing range.
- `messages/` is organized by domain: `component/`, `entity/`, `inventory/`, `master/`,
  `system/`, plus loose files (`account.proto`, `entity.proto`, `vec3.proto`).
- Naming convention: client→server messages end `*_cmsg.proto` → generated
  `*CMSG` (e.g. `messages/entity/attack_entity_cmsg.proto` → `AttackEntityCMSG`).
  Server→client messages end `*_smsg.proto` → `*SMSG`. Bidirectional/shared messages
  have no suffix (`master.proto` → `Master`, `ping.proto` → `Ping`/`Pong`).

**Codegen is two separate pipelines that both must run after editing a `.proto`:**
- Kotlin (zone-server, login-server): automatic via the Gradle `com.google.protobuf`
  plugin — regenerated on build into `bnet-messages/build/generated/source/proto/...`.
- C# (bestia-client): **manual** — see the [gen-protobuf](../gen-protobuf.md) skill.
  Run `bnet-messages/gen-protobuf.bat` from inside `bnet-messages/`; it clears
  `bestia-client/src/Bnet/Proto/` and calls `protoc.exe` once per `.proto` file. The
  generated C# is committed to the repo — always regenerate and commit it together
  with the `.proto` change.

## Wire routing: Netty → Envelope → CMSG → handler

zone-server owns a raw TCP socket (not WebSocket) via Netty, listening on
`socket.ip-address`/`socket.port` (`127.0.0.1:8090` in dev,
`zone-server/src/main/resources/application.yml`).

Pipeline, built in `zone-server/src/main/kotlin/net/bestia/zone/socket/SocketServer.kt:41-52`
(one `ClientMessageHandler` instance per connection):

```
LengthFieldBasedFrameDecoder (4-byte length prefix, 1 MB max frame)
  → ProtobufDecoder(EnvelopeProto.Envelope)
  → ProtobufEncoder
  → BigEndianLengthFieldPrepender   (outbound length prefix)
  → ClientMessageHandler
```

Inbound flow:

1. `ClientMessageHandler.channelRead0` (`socket/ClientMessageHandler.kt:62`) — if the
   channel isn't authenticated yet, routes to `authenticateChannel`; otherwise wraps
   the raw `Envelope` in `MessageEnvelopeReceivedEvent(this, accountId, msg)` and
   publishes it as a Spring `ApplicationEvent`.
2. `BnetMessageProcessorAdapter.handleMessageEnvelopeReceived`
   (`message/processor/BnetMessageProcessorAdapter.kt:27`), an `@EventListener`,
   pattern-matches the `oneof` via `when { envelope.hasXxx() -> ... }` and converts the
   raw protobuf into an internal `CMSG` object (e.g.
   `envelope.hasAttackEntity() -> AttackEntityCMSG.fromBnet(accountId, envelope.attackEntity)`).
   Unmatched envelopes throw `UnknownBnetMessageException`. **Adding a new incoming
   message type means adding a branch here.**
3. `InMessageProcessor.process()` (`message/processor/InMessageProcessor.kt:37`) looks
   up handlers by `msg::class` from a `Map<KClass<*>, List<IncomingMessageHandler<*>>>`
   built from every Spring-injected `IncomingMessageHandler<*>` bean — dispatch is by
   Kotlin class, not a string/int tag. See
   `message/processor/handler/SelectEntityHandler.kt` for the pattern to follow when
   adding a handler.

Outbound flow: an `SMSG` implementation (`message/SMSG.kt`) provides
`toBnetEnvelope(): EnvelopeProto.Envelope`; `OutMessageProcessor` /
`OutMessageHandler` route it to `ChannelRegistry.sendMessage()`
(`socket/ChannelRegistry.kt`), which looks up the Netty `Channel` by `accountId` and
calls `writeAndFlush`.

`ChannelRegistry` (accountId → Netty `Channel`) and
`ConnectionInfoService` (`ecs/session/ConnectionInfoService.kt`, accountId → `Session`
sealed class tracking the selected master/owned player entities/active entity) are the
two session maps — there is no single unified `Session` object.

## zone-server ECS (game loop)

Two ECS implementations currently coexist under `zone-server/src/main/kotlin/net/bestia/zone/`:

- **`ecs/`** — the production implementation, hand-rolled (no external ECS library).
  `Entity` (`ecs/Entity.kt`) is a data class over `MutableMap<KClass<out Component>, Component>`.
  `EntityManager` (`ecs/EntityManager.kt`) is a `ConcurrentHashMap<EntityId, Entity>`
  with per-entity read/write locks. `ZoneServer` (`ecs/ZoneServer.kt`, `@Service`) runs
  a **single dedicated thread** (`Executors.newFixedThreadPool(1)`) doing a fixed-tick
  loop — `world.tick-rate: 20` (Hz) in `application.yml` — over Spring-injected
  `List<IteratingSystem>` (every tick) and `List<PeriodicSystem>` (own cadence), each
  entity processed under `withEntityWriteLock`. Subpackages: `battle/`, `item/`,
  `movement/`, `network/`, `persistence/`, `player/`, `session/`, `spawn/`, `status/`,
  `visual/`.
- **`ecs2/`** — a newer, experimental rewrite added in commit `99ea8a1` ("Testing a
  seperate ECS implementation"). Centered on `ecs2/World.kt`: `ComponentStore`,
  `SystemScheduler` (parallel "wave" scheduling), `CommandQueue`, `ChangeTracker`,
  `Outbox`, with Spring wiring in `ecs2/spring/`. As of this writing it is **not yet
  wired into the live game loop** — it coexists alongside `ecs/` rather than replacing
  it. Check `ecs2/spring/Ecs2Runner.kt` / recent commits before assuming which one is
  live in a given area of code.

To add game logic: prefer `ecs/` unless you've confirmed `ecs2/` has since become the
live loop — implement `IteratingSystem` or `PeriodicSystem`, register as a Spring bean,
mutate/read entities via the injected `ZoneOperations`/`ZoneServer`.

## AI module

`net.bestia.zone.ai` (added in `6ff0a6f4`, built on top of `ecs/`, not `ecs2/`) layers
several classic game-AI techniques on the tick loop:

- `ai/perception` — `PerceptionSystem`, `Percept`, `AiEvent`
- `ai/memory` — `Blackboard`, `IndividualMemory`, `SharedMemoryService`
- `ai/behavior` — behavior-tree nodes (`BtNode`, composites, leaves like
  `MeleeAttackLeaf`, `FleeLeaf`, `WanderLeaf`)
- `ai/goal` + `ai/goal/consideration` — utility AI (`UtilityScorer`, `ResponseCurve`,
  goals like `FleeGoal`/`KillEnemyGoal`/`WanderGoal`)
- `ai/planner` — GOAP (`GoapPlanner`, `GoapAction`, `WorldState`)
- `ai/profile` — `AiProfile`/`AiProfileRegistry`, driven by YAML under
  `zone-server/src/main/resources/ai/*.yml` (e.g. `aggressive-melee.yml`)

Two ECS systems drive it every tick: `ai/ecs/AiThinkSystem.kt` (perception → decision,
populates a `Brain` component) and `ai/ecs/AiActSystem.kt` (executes the result).

## login-server & the login↔zone handoff

login-server is a plain Spring Boot REST service (`spring-boot-starter-web`, no
sockets) — authentication only, it never touches the game world. Key packages under
`login-server/src/main/kotlin/net/bestia/login/`: `account/loginmethod`
(`NftLoginMethod`, `StaticTokenLoginMethod`), `eip712` (wallet-signature auth),
`ethereum` (web3j NFT-ownership checks), `staticlogin` (dev-only login +
`DevAccountSeeder`), `jwt`.

`LoginController` (`POST /api/v1/login`) returns a signed JWT from
`JwtService.createLoginToken(accountId, role)` — `issuer("login")`,
`audience("zone")`, `claim("role", role.name)`. The client sends that token as the
socket's `Authentication` message payload; zone-server independently re-validates it
in `LoginTokenValidator.validateLoginToken`
(`zone-server/src/main/kotlin/net/bestia/zone/jwt/LoginTokenValidator.kt`), checking
issuer/audience against a **shared secret string** configured separately in each
server's `application.yml` (`jwt.secret` in login-server, `zone.jwt-auth-secret-key`
in zone-server — currently both the placeholder `"your-secret-key-here-change-in-production"`).
There is no DB call between the two servers; trust is entirely in the JWT signature.

Auth success on the socket triggers `AccountConnectedEvent` →
`AccountEntityControlService.handleAccountConnected`, which registers authorities into
`ConnectionInfoService` but does **not** spawn a game entity yet — that happens later
once the client picks a master (`SelectMasterCMSG` → `ConnectionInfoService.activateSession`).
On disconnect, the master's entity gets a `PersistAndRemove` component
(`ecs/persistence/PersistAndRemoveSystem.kt`) for async persist-then-remove, rather
than being removed synchronously.

## Database

Both servers use **H2, in-memory, schema-per-boot** — `spring.jpa.hibernate.ddl-auto: create`
in both `application.yml`s means the schema is dropped and recreated on every start.
No Flyway/Liquibase. ORM is Spring Data JPA/Hibernate. Each server defines its own
`Account` JPA entity independently (`login-server/.../account/Account.kt` vs
`zone-server/.../account/Account.kt`), linked only by convention
(`loginAccountId: Long`), not a shared entity class. H2 web console is enabled on both.
