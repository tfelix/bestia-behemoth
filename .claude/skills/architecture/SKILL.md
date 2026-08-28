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
/ `ActivateSkillHandler` (`zone-server/.../battle/attack/`), added for
player-triggered skill activation, plus an SMSG example such as `DamageEntitySMSG`.
Use those files as a template instead of re-deriving the shape from scratch.

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

- **`ecs/core/`** — the engine itself. Centered on `ecs/core/World.kt`: `ComponentStore`
  (sparse set, one per concrete component class — there are no archetypes), `SystemScheduler`
  ("wave" scheduling from declared read/write component sets), `CommandQueue`,
  `EntityRegistry`, `AsyncJobExecutor`. Spring wiring is `ecs/EcsConfiguration.kt`, which
  collects every `System` bean into one `World`; `ecs/EcsRunner.kt` is an optional standalone
  tick driver and `ecs/ZoneEngine.kt` is the real one (thread `zone-tick`).
- Game logic implements `ecs/core/System.kt` — `update(world, deltaTime)` plus a `schedule`
  (`EveryTick` / `EveryTicks(n)` / `EverySeconds(s)`) and `reads`/`writes` sets — and registers
  as a Spring `@Component` with an `@Order(n)` that fixes registration order.
- Domain subpackages sit alongside `core/`: `battle/`, `bestia/`, `item/`, `movement/`,
  `persistence/`, `spawn/`, ... — components + systems per gameplay area.
- `ecs/place/` names positions: `Place` (owner-only, where a player is in words) and `AreaName` (public,
  the label on a town or claim). `PlaceNameService` owns the one rule that resolves a position to a single
  name - narrowest area wins, region otherwise - against `AreaNameRegistry` plus the world's region
  partition. `PlaceSystem` re-resolves only where `Position` is dirty. Generated settlements and
  player-founded areas share the one registry deliberately, so there is a single resolver.

Three things that bite:

- **`reads`/`writes` are the whole contract.** `SystemScheduler.conflicts()` looks at nothing
  else, and non-conflicting systems are placed in the *same* wave. A system that mutates a
  component it only declared under `reads` therefore appears to conflict with nobody, and any
  ordering it depends on holds only by luck of registration order. Declare honestly, including
  components your helpers write to *other* entities.
- **`parallel-systems` is off, and cannot simply be switched on.** The world lock is a single
  `ReentrantLock` held for the entire tick, with `scheduler.tick` inside it, so a system running
  on a pool thread that calls any locked accessor (`world.get`/`add`/`has`/`isAlive`) blocks
  forever. Only `world.query(...).each { get<T>() }` is lock-free. `World.kt`'s own KDoc says the
  coarse lock is "only meaningful when `parallelSystems` is disabled".
- **Structural changes are deferred.** `add`/`remove`/`destroy` inside `update()` queue until the
  end of the tick, so a component added mid-tick is not visible later in the same tick. Use
  `World.defer { }` when it must apply immediately.

Off-tick code must go through `WorldView` (a lock-holding `read`/`modify` scope, or `send(command)`),
never `World` directly. Never block on I/O on the tick thread — use `AsyncJobExecutor`.

## AI module

`net.bestia.zone.ai`, built on `ecs/core/`: **GOAP chooses and sequences goals, behaviour trees carry
each step out.** Layered so the bottom is domain-agnostic:

| Package | Contents |
|---|---|
| `ai/core/state` | `StateKey<T>` (typed, carries a `MemoryScope` and an `observed` flag), immutable `WorldState`, live `Blackboard` with per-fact TTL |
| `ai/core/behavior` | The execution contract: `BtNode`, `Status`, `BtContext`, `ImmediateSuccess` |
| `ai/core/{action,precondition,effect,goal}` | Grounded `Action` (pre/effects/cost, `behavior` **and** `Posture`), `ActionTemplate`, `Precondition(s)`, `Effect(s)`, `Goal` (availability vs desired state), `Priority`/`Curve` DSL |
| `ai/core/planner` | Forward-A\* `Planner`, `Plan`, `EffectWriteBack` (scope-aware), `PlanExecutor` (simulation harness for domain tests only) |
| `ai/core/agent` | `Agent` interface + `SimpleAgent` for tests |
| `ai/bt` | The tree library: `SequenceNode`/`SelectorNode`/`ParallelNode`, `Inverter`/`Succeeder`/`Repeat`/`Cooldown`, the `sequence { } / selector { }` DSL, `Locomotion`, parameterised leaves (`MoveTo`, `FleeFrom`, `Wander`, `UseSkill`, `Wait`, `Sleep`) |
| `ai/perception` | `PerceptionSystem` — the **only** writer of observation keys; `SenseSystem` + `Sense`/`SenseContext` — the agents' eyes and ears, a periodic sweep hosting pluggable senses; `ForageSense` + `ForageGround`/`BiomeForageGround` — the one sense today, writer of `KNOWN_VEGETATION` from the world's biome raster |
| `ai/ecs` | `AiAgent` component, `AiDriveSystem`, `AiThinkSystem`, `AiActSystem`, `AiAgentFactory`, `SharedMemoryService`, `PlayerControlled` |
| `ai/domain/bestia` | `BestiaDomain`: the keys, goals and action templates for mobs; `ActivityCycle` (diurnal/nocturnal/cathemeral) |
| `ai/profile` | `AiProfileDto`/`AiProfile`/`AiProfileRegistry` + `AiConfig`/`IdleStance`, from `resources/ai/*.yml` |
| `ai/message` | `SetBestiaAiConfigCMSG`, `BestiaAiConfigSMSG`, `AiConfigErrorSMSG` and the handler |

Five ECS systems, each in its **own scheduler wave** (they conflict by declaring `AiAgent` as
written — that is deliberate and `AiSchedulingTest` enforces it):

```
PerceptionSystem  @Order(10)  EverySeconds(0.5) -> writes observation keys into agent.memory,
                                                   including IS_NIGHT from BestiaClock
SenseSystem       @Order(11)  EverySeconds(0.5) -> runs each registered Sense over every agent, each on
                                                   its own Sense.intervalSeconds (ForageSense: 2s)
AiDriveSystem     @Order(15)  EverySeconds(1)   -> moves hunger/tiredness/restlessness, ticks TTLs;
                                                   tiredness runs *backwards* while asleep
AiThinkSystem     @Order(20)  EveryTick         -> selects a goal; replans only if it changed or the
                                                   plan is spent; staggered per entity by tickCount
AiActSystem       @Order(30)  EveryTick         -> ticks the current step's tree; on SUCCESS applies
                                                   that action's effects, then advances the plan.
                                                   Also derives the `Animation` component from the
                                                   current step's `Posture` plus `Path`
```

Four rules worth knowing before touching any of it:

1. **Perception owns observations; effects own beliefs.** A key created with `observed = true`
   (position, health, target, aggro, night) may be *simulated* during A\* — a walk action has to be
   able to imagine arriving — but `EffectWriteBack` refuses to persist it. Only perception writes
   those. This is what stops an agent believing what it merely planned. Perception may *clear* a
   belief its observations contradict (`SAFE`, `RESTED`); it never asserts one.
2. **Effects apply on observed success**, one action at a time, from `AiActSystem` — not for a whole
   plan at plan time.
3. **Nothing plans before it has perceived** (`AiAgent.hasPerceived`). An empty blackboard is not a
   neutral start: the search will otherwise resolve unknowns by assuming an action's effects.
4. **A goal whose desired state already holds is skipped**, so a behaviour that should persist while
   some condition lasts needs a belief key that stays unsatisfied throughout it. `RESTED` is that key
   for sleeping through the night; a bare tiredness ceiling is met by a rested creature, so without it
   a diurnal animal would have ambled about in the dark.

To add a **sense** (something creatures notice): implement `Sense` as a `@Component`, give it an
`intervalSeconds` and declare any components it reads. `SenseSystem` collects every such bean, folds
their `reads` into its own, and runs each on its own cadence; write facts through
`SenseContext.remember`, which routes to the individual/pack/world board by the key's `MemoryScope`.
No new ECS system, no new scheduler wave. `PerceptionSystem` is the obvious candidate to fold in here
as a `SightSense` and has not been yet.

To add a behaviour: add a `StateKey`, a `Goal` (priority formula in the Kotlin
`priority { consider(...) }` DSL), and an `ActionTemplate` that grounds concrete actions *with their
behaviour trees*; then name the goal and action ids in a `resources/ai/*.yml` profile. YAML selects
and tunes numbers only — it cannot express behaviour, and the registry fails the boot on an unknown
id. Mobs get their profile from `mob/*.yml`'s `ai:` key via `BestiaEntitySpawner`; player-owned
bestias get one from `PlayerBestiaEntitySpawner` narrowed by the owner's persisted `AiConfig`.

Profiles today: `aggressive-melee` (hunts on sight, flees when hurt), `passive-wanderer` (grazer that
runs early), `passive-day-active` (diurnal grazer that never flees and hunts whoever hits it).

## login-server & the login↔zone handoff

login-server is a plain Spring Boot REST service (`spring-boot-starter-web`, no
sockets) — authentication only, it never touches the game world. Key packages under
`login-server/src/main/kotlin/net/bestia/login/`: `account/loginmethod`
(`NftLoginMethod`, `WebAuthnCredential`), `eip712` (wallet-signature auth), `ethereum`
(web3j NFT-ownership checks), `webauthn` (passkeys), `gamelogin` (browser-mediated
login sessions), `recovery`, `jwt`.

`account.sign-up-role` (`AccountConfig`) is the role every passkey registration is
created with — `USER` in `application.yml`, raised to `SUPER_GM` by
`application-dev.yml`, which is how a dev host gets a GM account.

Storage is MariaDB (`login-server/compose.yaml`, port 3307) with Flyway owning the
schema (`src/main/resources/db/migration/`) and `ddl-auto: validate` checking it. This
is the only module with migrations.

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

Two login methods now converge on that same `createLoginToken` call, so nothing
downstream of the socket handshake can tell them apart:

- `POST /api/v1/auth/eip712sig` → `POST /api/v1/login` — wallet signature, refresh token.
- **Passkeys / WebAuthn**, which never touch the game client. The client calls
  `POST /api/v1/auth/game/start` with a loopback `redirect_uri` and a PKCE challenge,
  opens the returned URL with `OS.ShellOpen`, and waits on a `TcpListener` bound to
  `127.0.0.1:0` (`bestia-client/src/Auth/`). The system browser runs the ceremony
  against `/api/v1/webauthn/**`, `POST /api/v1/auth/session/complete` mints a 60-second
  single-use code, and the game trades it at `POST /api/v1/auth/game/exchange` for the
  same zone JWT. WebAuthn itself is entirely the browser's problem — there is no native
  passkey code on any platform, and no `bestia://` URI scheme (RFC 8252 §7.3; Safari and
  Firefox refuse custom-scheme redirects anyway).

An account may hold any number of `WebAuthn Credential` rows. They are joined to it by
one `webauthn_user.user_handle`, which is what makes a synced passkey created on one
machine resolve to the same account on another — the backend has no concept of a device.

Auth success on the socket triggers `AccountConnectedEvent` →
`AccountEntityControlService.handleAccountConnected`, which registers authorities into
`ConnectionInfoService` but does **not** spawn a game entity yet — that happens later
once the client picks a master (`SelectMasterCMSG` → `ConnectionInfoService.activateSession`).
On disconnect, the master's entity gets a `PersistAndRemove` component
(`ecs/persistence/PersistAndRemoveSystem.kt`) for async persist-then-remove, rather
than being removed synchronously.

## Database

The two servers no longer agree here, and the difference matters:

- **zone-server: MariaDB, `ddl-auto: update`** (`jdbc:mariadb://localhost:3306/bestia_zone`).
  `./gradlew :zone-server:bootRun` starts the container from `zone-server/compose.yaml` via
  spring-boot-docker-compose. Because the schema is *updated* rather than recreated, **game data
  survives a restart**: adding a column to a JPA entity is applied automatically, but existing rows
  keep their old values, so a new field needs a default that reads sensibly for rows written before
  it existed.
- **login-server: H2, in-memory, `ddl-auto: create`** — dropped and recreated on every start, with
  the H2 web console enabled.

No Flyway/Liquibase on either. ORM is Spring Data JPA/Hibernate. Each server defines its own
`Account` JPA entity independently (`login-server/.../account/Account.kt` vs
`zone-server/.../account/Account.kt`), linked only by convention (`loginAccountId: Long`), not a
shared entity class.
