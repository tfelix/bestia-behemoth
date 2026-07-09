---
name: architecture
description: Explains the bestia-behemoth server architecture — protobuf message contracts (bnet-messages), the Envelope wire format, Netty socket handling, message dispatch/handlers, the ECS (entity/component/system) game loop with parallel "wave" scheduling, the login-server↔zone-server JWT handoff, and the AI module. Read this BEFORE investigating "how does message X travel from client to server", "where does system Y live", "how do I add a new message type", or any other cross-cutting networking/ECS question — it gives file:line pointers so the answer doesn't have to be re-derived from scratch. Triggers on: protobuf, .proto, Envelope, CMSG, SMSG, Netty, socket server, zone-server, login-server, ECS, ecs/core, entity component system, message handler, JWT auth, bnet-messages, ChannelRegistry, World tick, gen-protobuf.
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
   (`message/BnetMessageProcessorAdapter.kt:32`), an `@EventListener`,
   pattern-matches the `oneof` via `when { envelope.hasXxx() -> ... }` and converts the
   raw protobuf into an internal `CMSG` object (e.g.
   `envelope.hasAttackEntity() -> AttackEntityCMSG.fromBnet(accountId, envelope.attackEntity)`).
   Unmatched envelopes throw `UnknownBnetMessageException`. **Adding a new incoming
   message type means adding a branch here.**
3. `InMessageProcessor.process()` (`message/InMessageProcessor.kt:36`) looks
   up handlers by `msg::class` from a `Map<KClass<*>, List<IncomingMessageHandler<*>>>`
   built from every Spring-injected `IncomingMessageHandler<*>` bean — dispatch is by
   Kotlin class, not a string/int tag. See
   `entity/SelectEntityHandler.kt` for the pattern to follow when
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

## Adding a new message type end-to-end

Every step below, worked through once already for a real feature: `ActivateSkillCMSG`
/ `SkillActivatedSMSG` / `ActivateSkillHandler` (`zone-server/.../battle/attack/`),
added for player-triggered skill activation. Use those three files as a template
instead of re-deriving the shape from scratch.

1. **Proto**: new file under `bnet-messages/src/main/proto/messages/<domain>/`
   (`*_cmsg.proto` / `*_smsg.proto`), then wire it into `envelope.proto`: an `import`
   line plus a field in the `oneof` inside the correct numbered range (see above).
   Field numbers per range are hand-assigned sequentially — take the highest existing
   number in that block + 1, don't reuse or leave gaps.
2. **Kotlin CMSG** (incoming): `data class XyzCMSG(override val playerId: Long, ...) : CMSG`
   with `companion object fun fromBnet(accountId: Long, proto: XyzCmsgProto.XyzCMSG): XyzCMSG`.
   Template: `battle/attack/AttackEntityCMSG.kt`.
3. **Dispatch branch**: add `envelope.hasXyz() -> XyzCMSG.fromBnet(accountId, envelope.xyz)`
   to the `when` in `BnetMessageProcessorAdapter.kt` (line 37+) plus an import — this is
   the one manual registration point, everything downstream auto-wires via Spring.
4. **Handler**: `@Component class XyzHandler(...) : InMessageProcessor.IncomingMessageHandler<XyzCMSG>`
   with `override val handles = XyzCMSG::class` — auto-discovered by
   `InMessageProcessor` through Spring's injected `List<IncomingMessageHandler<*>>`, no
   manual registry entry needed. **Resolve the acting entity via
   `ConnectionInfoService.getActiveEntityId(msg.playerId)`**, never a client-supplied
   entity ID — this is the pattern used by every handler that acts "on behalf of
   whichever entity is currently selected" (`GetSkillsHandler`, `ChatHandler`,
   `UseItemHandler`, `MoveActiveEntityHandler`, ...); it already resolves
   master-vs-owned-bestia and needs no separate ownership check.
5. **Kotlin SMSG** (outgoing), if a reply/broadcast is needed: `data class XyzSMSG(...) : SMSG`
   implementing `toBnetEnvelope()`. Two templates depending on shape: a one-off
   broadcast event (`battle/damage/DamageEntitySMSG.kt`, sent via
   `OutMessageProcessor.sendToAllPlayersInRange(pos, msg)`), or persistent entity-state
   sync (`ecs/status/SkillPointsSMSG.kt`'s owning component implements `Dirtyable` +
   `toEntityMessage()` and is auto-pushed on change — only use this shape for actual
   entity state, not one-off events).
6. **C# client wrappers**: outgoing message is an `ICMSG` subclass under
   `bestia-client/src/Bnet/Message/<Domain>/XyzCMSG.cs` (template:
   `Message/Entity/AttackEntityCMSG.cs`) implementing `ToEnvelope()`. Incoming message
   is an `EntitySMSG`/`ISMSG` subclass (template: `Message/Entity/DamageEntitySMSG.cs`)
   with a static `FromProto(...)`, plus a new `else if (envelope.Xyz != null) { ... }`
   branch in `BnetSocket.cs`'s dispatch chain (`_Process`, ~line 60-183) — messages not
   handled there just print an unhandled-envelope warning instead of failing loudly.
   GDScript side: add a thin wrapper method to `connection_manager.gd` that
   instantiates and sends the CMSG (see `send_attack_entity`/`get_skills`); incoming
   `EntitySMSG` subclasses are already caught generically by the `entity_received`
   signal in `_on_bnet_socket_message_received` — no per-message branch needed there.
7. **Regenerate + build**: run `bnet-messages/gen-protobuf.bat` (works from any working
   directory — every path, including `protoc.exe` itself, is anchored to the script's
   own location via `%~dp0`, so it does **not** depend on `protoc.exe` being on `PATH`)
   to regenerate the C# proto classes and commit them with the `.proto` change; the
   Kotlin side regenerates automatically on the next Gradle build, no manual step.

## zone-server ECS (game loop)

`zone-server/src/main/kotlin/net/bestia/zone/ecs/` is the hand-rolled ECS (no external
ECS library):

- **`ecs/core/`** — the engine itself (formerly the standalone `ecs2` package, merged in).
  Centered on `ecs/core/World.kt`: `ComponentStore`, `SystemScheduler` (parallel "wave"
  scheduling based on declared read/write component sets), `CommandQueue`,
  `ChangeTracker`, `Outbox`, with Spring wiring in `ecs/core/spring/` (`Ecs2Configuration`
  collects every `Ecs2System` bean into a `World`; `Ecs2Runner` is the optional tick
  driver). Game logic implements `Ecs2System` (`update(world, deltaTime)`, declaring
  `reads`/`writes` component sets) and registers as a Spring `@Component` bean.
- Domain subpackages sit alongside `core/`: `battle/`, `bestia/`, `item/`, `movement/`,
  `persistence/`, `player/`, `session/`, `spawn/`, `status/` — components + systems per
  gameplay area, all built on `ecs/core/World`/`Ecs2System`.

To add game logic: implement `Ecs2System` (`ecs/core/Ecs2System.kt`), register it as a
Spring bean, mutate/read entities via the injected `World`.

## AI module

`net.bestia.zone.ai` (added in `6ff0a6f4`, built on top of `ecs/core/`) layers
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
