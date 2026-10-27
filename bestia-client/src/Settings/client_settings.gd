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

## The api-server, which serves the map: tiles, the names on them and the services in them. Its own server,
## apart from the game socket: tiles are bulk PNG traffic with their own caching. Renamed from
## `map_server_url` when the map moved off the zone, so a settings file that still names the zone's old port
## is ignored for this value rather than obeyed.
##
## An IPv4 literal rather than `localhost`, and it is load-bearing rather than cosmetic: Godot resolves
## `localhost` to `::1` first and [HTTPRequest] tries exactly one resolved address, so a server listening on
## 127.0.0.1 only would cost every tile request a connect timeout - a map filling in one row every half
## minute rather than an error.
@export var api_server_url: String = "http://127.0.0.1:8095"

## The game socket [BnetSocket] connects to, applied by [ConnectionManager] before it dials.
##
## Spelled separately from [member api_server_url] rather than sharing one host, because the two
## reach their server by different clients: .NET's `TcpClient` tries every address a name resolves
## to, so `localhost` is safe here in a way it is not there.
@export var game_server_host: String = "localhost"

@export var game_server_port: int = 8090

## How much the game socket reports about its traffic: "off", "summary" for one line per second per
## direction, or "detail" to add a line per message for whatever [member net_log_filter] allows.
##
## Summary is the shipped value rather than a debug-only one: one line a second is cheap, and it is
## what turns "it stutters near town" into a figure somebody can read.
@export var net_log_mode: String = "summary"

## Which message types "detail" prints, in the same syntax as zone-server's
## `socket.filter-log-messages`: a bare name allows, a "!" prefix denies, and an empty list allows
## everything. The names are the protobuf field names, so `state_batch` means the same on both
## sides.
##
## The shipped list denies what the server denies, plus the chunk payloads - the traffic that is
## either per-tick or kilobytes at a time, and so worth asking for by name rather than receiving by
## default.
@export var net_log_filter: PackedStringArray = PackedStringArray([
	"!state_batch",
	"!chunk_data",
	"!chunk_patch",
	"!chunk_static_entities",
	"!chunk_ground_overlay",
])

## Distance (world units) within which the mouse's ground position must be from an entity for
## entity-target skills (AttackResource.target_type ENEMY/FRIENDLY) to visually snap onto it. See
## MouseStateSkillTargeting._update_entity_snap.
@export var skill_target_snap_distance: float = 1.5

@export_group("Audio")

## Linear bus volumes from 0 to 1, read by the AudioManager autoload. One per bus in `default_bus_layout.tres`.
@export_range(0.0, 1.0, 0.01) var master_volume: float = 1.0
@export_range(0.0, 1.0, 0.01) var music_volume: float = 0.7
@export_range(0.0, 1.0, 0.01) var ambience_volume: float = 0.8
@export_range(0.0, 1.0, 0.01) var effects_volume: float = 1.0
@export_range(0.0, 1.0, 0.01) var interface_volume: float = 0.8

@export_group("Video")

## Borderless fullscreen rather than a window, applied by the DisplayManager autoload.
@export var fullscreen: bool = false

@export var vsync: bool = true
