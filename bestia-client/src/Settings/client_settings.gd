extends Resource
class_name ClientSettings

## Everything the client reads out of configuration, in one place.
##
## The exported properties here are the schema: `default_settings.tres` supplies the shipped values
## and `user://settings.json` overrides them by property name, so adding a setting means adding one
## `@export` and nothing else. See [SettingsManager], which owns the loading.

## Bumped when a stored file's shape stops being readable. [SettingsManager] discards a file whose
## version it does not recognise rather than half-applying it.
const FORMAT_VERSION := 1

## Hosts the passkey pages the system browser opens and the endpoints [PasskeyLoginService] calls to
## exchange the resulting one-time code for a signed JWT.
@export var login_server_url: String = "http://localhost:8080"

## Zone-server's map tile endpoint. A different port from the game socket on purpose: tiles are bulk
## PNG traffic with their own caching, and the two are meant to be firewalled and scaled apart. See
## `zone-server/src/main/resources/application.yml`.
##
## An IPv4 literal rather than `localhost`, and it is load-bearing rather than cosmetic:
## `server.address` in that file pins Tomcat to 127.0.0.1, so nothing answers on `::1` at all. Godot
## resolves `localhost` to `::1` first and [HTTPRequest] tries exactly one resolved address, so the
## wrong spelling here would not fail - every tile request would pay a connect timeout, which reads
## as a map filling in one row every half minute rather than as a connection problem. This line and
## `server.address` move together or not at all.
@export var map_server_url: String = "http://127.0.0.1:8091"

## The game socket [BnetSocket] connects to, applied by [ConnectionManager] before it dials.
##
## Spelled separately from [member map_server_url] rather than sharing one host, because the two
## reach their server by different clients: .NET's `TcpClient` tries every address a name resolves
## to, so `localhost` is safe here in a way it is not there.
@export var game_server_host: String = "localhost"

@export var game_server_port: int = 8090

## Distance (world units) within which the mouse's ground position must be from an entity for
## entity-target skills (AttackResource.target_type ENEMY/FRIENDLY) to visually snap onto it. See
## MouseStateSkillTargeting._update_entity_snap.
@export var skill_target_snap_distance: float = 1.5
