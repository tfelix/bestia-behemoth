class_name MapSource
extends Node

## Fetches map tiles over HTTP and remembers them, in memory and on disk.
##
## One of these is shared by every view of the map - the minimap and the full overlay look at the same
## world through the same cache, so panning the overlay warms the minimap and the other way round.
##
## [b]Why HTTP and not the game socket.[/b] A screen of map is a few hundred kilobytes of PNG that gets
## re-requested every time the window opens. Conditional requests, a cache that survives a restart and a
## transfer that cannot delay a movement packet are all things HTTP already does.
##
## [b]Where and how.[/b] From the api-server, the companion app's server, under the selected master's id. It
## is sent the ticket the zone hands over in [code]AuthenticationSuccess[/code], which lives exactly as long as
## the connection, and asks the zone whose ticket it is. Deliberately not the login JWT this once sent: that
## expires minutes after the login server issued it and the socket only checks it once, so the map quietly
## stopped working partway into a session. See [method ConnectionManager.http_ticket].
##
## [b]What the server never sends.[/b] Nothing tells this when the player's charts change. A chart is an
## item, so the inventory sync already carries the news - see [method set_chart_signature], called from
## [code]ui.gd[/code] whenever the set of charts held changes.

## A tile arrived. [param key] is [method key_of]'s form.
signal tile_ready(key: String, texture: Texture2D)

## The server has no tile there for this player: they have charted none of that ground.
signal tile_absent(key: String)

## The names of what stands on a tile arrived. [param places] is an [Array] of [Dictionary], as
## [method places_of] returns them.
signal places_ready(key: String, places: Array)

## The services on a tile arrived, or changed since they were last asked about. [param services] is an
## [Array] of [Dictionary], as [method services_of] returns them.
##
## Not emitted for an answer that says nothing changed, which is most of them: a listener can treat every
## firing as news.
signal services_ready(key: String, services: Array)

## World geometry and the level range, from the map's [code]/meta[/code]. Nothing can be addressed before it.
signal meta_ready(meta: Dictionary)

## A habitat overlay for [param key] arrived, for the species set with [method set_habitat_species].
signal habitat_ready(key: String)
## The species whose habitat is shown changed; 0 when none is.
signal habitat_species_changed(species_id: int)

## The server refused this client's ticket, so nothing more will be asked for.
##
## Exists because the alternative is what this used to do: treat a 401 as a transient failure and re-ask every
## five seconds for every tile on screen, for the rest of the session, with nothing but a log line to say so.
## A credential that is not accepted is not a slow tile, and the player is owed the difference.
##
## Carries no reason: there is one, and whoever shows it needs the wording rather than the diagnosis.
signal map_unavailable()

## Concurrent requests. Godot's HTTPRequest is one-at-a-time per node, so this is literally a node count.
## Six is about a screen of tiles in two waves without opening a connection per tile.
const _POOL_SIZE := 6

## Absent tiles are the common case - most of the world is uncharted - so remembering them matters as much
## as remembering the images. Capped because a player panning across an empty world would otherwise
## accumulate one entry per tile of it.
const _MAX_ABSENT := 4096

## How long a tile that failed is left alone before it may be asked for again.
##
## Not a nicety. [MapView] follows the player, so it redraws every frame, and a redraw asks for every tile
## it does not have - which means a tile answering with an error is re-requested sixty times a second for
## as long as it is on screen, holding all [constant _POOL_SIZE] slots busy while the rest of the map
## queues behind it. A failure has to be remembered for a while, or it is not a failure but a loop.
const _RETRY_AFTER_SECONDS := 5.0

const _MAX_RETRY_AFTER := 4096

## Attempts at the map's [code]/meta[/code], and the wait between them.
##
## Retried because it can legitimately fail at the moment it is first asked - the api-server or the zone
## behind it busy or still starting - and nothing else asks again: without a map version nothing can be
## addressed, so one lost request would mean no map for the whole session.
const _META_ATTEMPTS := 10
const _META_RETRY_SECONDS := 1.5

## What is being asked about a tile: [code]t[/code] for the picture, [code]p[/code] for the names of what
## stands on it, [code]s[/code] for the services that are open for business on it.
##
## One queue and one pool serve all three, because they are the same request pattern against the same
## addressing - and because a screen of map wants them all at once, so a second pool would race this one for
## the same six connections instead of sharing them. Everything keyed per request - the queue, what is in
## flight, what is known absent, what is backing off - is keyed by this rather than by the bare tile key, so a
## picture and a name list can be in flight, absent or backing off independently.
const _WANT_TILE := "t"
const _WANT_PLACES := "p"
const _WANT_SERVICES := "s"
## Where one species lives, laid over the picture. Its key carries the species in front of the tile key.
const _WANT_HABITAT := "h"

## What each kind's path ends in. The services endpoint has no extension: it is a live answer rather than a
## file, and the server routes it as one.
const _SUFFIXES := {
	_WANT_TILE: ".png",
	_WANT_PLACES: ".json",
	_WANT_SERVICES: "",
	_WANT_HABITAT: ".png",
}

const _CACHE_ROOT := "user://mapcache/"

## World geometry, empty until [signal meta_ready]. Read it rather than assuming 256 or level 9.
var meta: Dictionary = {}

## Tiles every player sees identically. See [method _is_shared] for how one is recognised.
var _shared: Dictionary = {}

## The names of what stands on each tile, as key -> [Array] of [Dictionary].
##
## [b]Memory only, unlike the tiles.[/b] The disk cache exists because a tile is hundreds of kilobytes and
## decoding a screen of them is a visible hitch at login; a tile's places are a few hundred bytes of JSON, so
## the argument does not transfer and neither does the complexity - no index, no write-through, no directory
## to sweep when the chart set changes. They are re-asked for once per session and that is the whole cost.
var _places: Dictionary = {}

## The services on each tile, as key -> [Array] of [Dictionary].
##
## [b]Memory only, and never trusted for long.[/b] A place is a fact about the generated world; a service is a
## fact about the world as it is now - a shop whose building is knocked down is gone from every map that
## covers it. So these are kept with the [code]ETag[/code] they came with and re-asked for on a schedule the
## view sets (see [method want_services]), and the disk cache never sees them: a stored answer would be a
## picture of a town that may since have burned.
var _services: Dictionary = {}

## The [code]ETag[/code] each tile's services came with, sent back as [code]If-None-Match[/code] so an
## unchanged tile costs a 304 and no body.
var _service_etags: Dictionary = {}

## When each tile's services were last confirmed - a 200 or a 304 - in [method Time.get_ticks_msec].
var _services_checked: Dictionary = {}

## Tiles masked to this player's charts. Dropped whenever those change - see [method set_chart_signature].
var _personal: Dictionary = {}

## The species whose habitat the map shows, or 0.
var habitat_species: int = 0

## Habitat overlays, as "species/level/x/y" -> [Texture2D]. Memory only: they change with the charts and are
## only wanted while the player is hunting one species.
var _habitat: Dictionary = {}

var _absent: Dictionary = {}

## Keys whose last request did not answer, and the earliest millisecond they may be asked for again.
var _retry_after: Dictionary = {}

var _idle: Array[HTTPRequest] = []
var _queue: Array[String] = []
var _in_flight: Dictionary = {}

## What is on disk, as key -> path, without having decoded any of it.
##
## Indexed rather than loaded: a well-travelled player's cache is thousands of files, and decoding them all
## at startup would trade the delay this exists to remove for a hitch of the same size. A tile is decoded by
## [method cached], when something actually draws it.
var _disk_shared: Dictionary = {}
var _disk_personal: Dictionary = {}

## Directory the shared tiles live in, or "" until the world version is known. Keyed by the server's
## worldMapVersion, so a regenerated world or a restyled map simply stops looking at the old directory
## instead of serving stale pictures out of it.
var _cache_dir: String = ""

## Directory the masked tiles live in - a subdirectory of [member _cache_dir] named for the chart set that
## produced them, or "" while there is no version or no charts.
var _personal_dir: String = ""

var _chart_key: String = ""

## Set once the server has refused our ticket. Nothing is requested while it stands, and nothing clears it:
## retrying the same ticket is asking the same question and expecting a different answer. A reconnect gets a
## fresh one because it also gets a fresh Game scene, and so a fresh one of these.
var _refused: bool = false


static func key_of(level: int, tx: int, ty: int) -> String:
	return "%d/%d/%d" % [level, tx, ty]


func _ready() -> void:
	for i in _POOL_SIZE:
		var request := HTTPRequest.new()
		request.name = "TileRequest%d" % i
		add_child(request)
		_idle.append(request)


## Asks for the world geometry, retrying until it answers. Safe to call more than once.
func fetch_meta() -> void:
	if not meta.is_empty() or not _may_request():
		return

	_fetch_meta_once(1)


## The tile if it is already here, else null. Never requests - ask [method want] for that.
##
## May read one file: a tile this player cached in an earlier session is decoded the first time something
## draws it. That is what the cache is for, and it is bounded by what is on screen rather than by what is
## on disk.
func cached(key: String) -> Texture2D:
	if _shared.has(key):
		return _shared[key]
	if _personal.has(key):
		return _personal[key]

	return _from_disk(key)


func is_absent(key: String) -> bool:
	return _absent.has(_WANT_TILE + ":" + key)


## What stands on [param key], as an [Array] of [Dictionary], or an empty one if it has not arrived.
##
## A tile with nothing on it and a tile whose names have not come back are deliberately the same answer here:
## both mean "draw no labels", and the caller has nothing different to do about them. [signal places_ready]
## is what says an answer landed.
##
## Each entry carries [code]kind[/code], [code]x[/code] and [code]y[/code] in world metres, and then whichever
## of [code]name[/code], [code]tier[/code] and [code]poi[/code] the server had for it - see
## [code]MapTileService.Place[/code]. A [code]name[/code] is a generated string and is English by
## construction; [code]poi[/code] and [code]kind[/code] are enum names, so a client builds a translatable key
## out of them instead.
func places_of(key: String) -> Array:
	return _places.get(key, [])


## Places within [param radius] metres of [param at], nearest first, at most [param limit] of them.
##
## Scans what is already cached rather than asking for anything: what is near the player is on screen or was
## a moment ago, so it is here. Ground they have never had a tile for is ground they have not charted, and
## there is nothing to say about it on a compass either.
##
## Nearest-first and capped because the consumer is a strip a few hundred pixels wide - a player standing in a
## district of a city is near dozens of places, and all of them at once is not a reading.
func places_near(at: Vector2, radius: float, limit: int) -> Array:
	var all: Array = []
	for key in _places:
		all.append_array(_places[key])

	return _nearest(all, at, radius, limit)


## The services on [param key], as an [Array] of [Dictionary], or an empty one if none have arrived.
##
## Each entry carries [code]id[/code], [code]kind[/code] (a [code]MapService[/code] enum name such as
## [code]MERCHANT[/code] or [code]WARD_STONE[/code]), and [code]x[/code] and [code]y[/code] in world metres;
## then [code]business[/code], [code]settlement[/code], [code]openFrom[/code] and [code]openTo[/code], any of
## which may be JSON null. The hours are in-game hours of the day.
func services_of(key: String) -> Array:
	return _services.get(key, [])


## Services within [param radius] metres of [param at], nearest first, at most [param limit] of them.
##
## [method places_near] for services, with one difference: the same service is listed by the tile of every
## level that covers it, and the minimap and the overlay look at different levels - so it is counted once.
func services_near(at: Vector2, radius: float, limit: int) -> Array:
	var seen := {}
	var once: Array = []
	for key in _services:
		for service in _services[key]:
			var identity := service_identity(service)
			if not seen.has(identity):
				seen[identity] = true
				once.append(service)

	return _nearest(once, at, radius, limit)


## What makes two service entries the same service. The kind as well as the id, because a building's id and a
## ward stone's are numbered by different things and are not promised not to meet.
static func service_identity(service: Dictionary) -> String:
	return "%s:%s" % [service.get("kind"), service.get("id")]


func _nearest(entries: Array, at: Vector2, radius: float, limit: int) -> Array:
	var found: Array = []

	for entry in entries:
		var to := Vector2(float(entry.get("x", 0.0)), float(entry.get("y", 0.0)))
		var distance := to.distance_to(at)
		if distance <= radius:
			found.append({"entry": entry, "distance": distance})

	found.sort_custom(func(a: Dictionary, b: Dictionary) -> bool: return a["distance"] < b["distance"])

	var nearest: Array = []
	for entry in found.slice(0, limit):
		nearest.append(entry["entry"])

	return nearest


## Requests the picture of [param key] unless it is already known, in flight, absent, or backing off.
func want(level: int, tx: int, ty: int) -> void:
	_want(_WANT_TILE, key_of(level, tx, ty))


## Shows where [param species_id] lives on the map, or nothing for 0. The server answers only for a species
## the master has seen.
func set_habitat_species(species_id: int) -> void:
	if species_id == habitat_species:
		return

	habitat_species = species_id
	_habitat.clear()
	habitat_species_changed.emit(species_id)


## The habitat overlay for [param key], or null when there is none yet or no species is shown.
func habitat_of(key: String) -> Texture2D:
	return _habitat.get(_habitat_key(key))


## Requests the habitat overlay for a tile, under the same conditions as [method want].
func want_habitat(level: int, tx: int, ty: int) -> void:
	if habitat_species != 0:
		_want(_WANT_HABITAT, _habitat_key(key_of(level, tx, ty)))


func _habitat_key(key: String) -> String:
	return "%d/%s" % [habitat_species, key]


## Requests the names of what stands on [param key], under the same conditions as [method want].
func want_places(level: int, tx: int, ty: int) -> void:
	_want(_WANT_PLACES, key_of(level, tx, ty))


## Requests the services on [param key] unless they were confirmed at or after [param checked_since_msec], on
## the [method Time.get_ticks_msec] clock - otherwise under the same conditions as [method want].
##
## The caller says how fresh is fresh enough, because the answer differs per view: the overlay is being read
## and re-asks every half minute, the minimap is glanced at and can wait a couple. A tile asked for again is
## asked with its [code]ETag[/code], so the usual answer is a 304 with nothing in it.
func want_services(level: int, tx: int, ty: int, checked_since_msec: int) -> void:
	var key := key_of(level, tx, ty)
	if _services_checked.has(key) and int(_services_checked[key]) >= checked_since_msec:
		return

	_want(_WANT_SERVICES, key)


func _want(kind: String, key: String) -> void:
	if not _may_request():
		return

	var id := kind + ":" + key
	# Services are never "had": whether they are fresh enough was decided by want_services.
	var have := false
	if kind == _WANT_TILE:
		have = cached(key) != null
	elif kind == _WANT_PLACES:
		have = _places.has(key)
	elif kind == _WANT_HABITAT:
		have = _habitat.has(key)
	if have or _absent.has(id) or _in_flight.has(id) or _queue.has(id):
		return
	if Time.get_ticks_msec() < int(_retry_after.get(id, 0)):
		return

	_queue.append(id)
	_pump()


## Whether there is any point sending a request at all.
##
## An empty ticket is the case that used to produce the second kind of 401: the map can be drawn before the
## zone has authenticated the connection, and sending [code]"Ticket "[/code] with nothing after it asks the
## server to refuse us. Not having a credential or a master yet is worth waiting out rather than asking about.
func _may_request() -> bool:
	return not _refused and not ConnectionManager.http_ticket().is_empty() and _master_id() >= 0


## Points this at the chart set the player is now holding, dropping everything that depended on the old one.
##
## [param signature] identifies the set rather than describing it - any string that changes when the held
## charts change will do, and [code]ui.gd[/code] builds one out of their instance ids.
##
## The masked tiles are kept [i]on disk[/i] under a directory named for the set that produced them, which is
## what puts the map on screen at login instead of a few seconds after it. No revalidation is needed for
## that to be safe: a masked tile is a function of the charts held, so while the signature matches, a stored
## tile is the tile the server would send back.
##
## The shared tiles are deliberately untouched. A fully charted tile is the same picture for everyone and
## does not become a different one when its owner charts more land - so charting does not throw away what
## was already complete, which is most of a well-travelled player's map.
func set_chart_signature(signature: String) -> void:
	var key := "" if signature.is_empty() else "c" + signature.sha256_text().substr(0, 16)
	if key == _chart_key:
		return

	_chart_key = key
	_personal.clear()
	_habitat.clear()
	_places.clear()
	_forget_services()
	_absent.clear()
	_retry_after.clear()
	_use_chart_set()


func _fetch_meta_once(attempt: int) -> void:
	var request := HTTPRequest.new()
	add_child(request)
	request.request_completed.connect(
		func(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
			request.queue_free()
			if not _accept_meta(result, code, body):
				_retry_meta(attempt)
	)

	var err := request.request(_map_base() + "/meta", _headers())
	if err != OK:
		push_warning("MapSource: could not start /meta request: %s" % err)
		request.queue_free()
		_retry_meta(attempt)


## Whether the answer was usable. Warns about what it rejected, so a retried failure is still visible.
func _accept_meta(result: int, code: int, body: PackedByteArray) -> bool:
	if code == 401:
		# Refused rather than retried: the ten attempts would be spent re-asking a question already answered,
		# and [method _retry_meta] stops of its own accord once this is set.
		_refuse()
		return false

	if result != HTTPRequest.RESULT_SUCCESS or code != 200:
		push_warning("MapSource: /meta failed (result=%s, code=%s)" % [result, code])
		return false

	var json := JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_DICTIONARY:
		push_warning("MapSource: /meta was not an object")
		return false

	meta = json.data
	_use_version(str(meta.get("worldMapVersion", "")))
	meta_ready.emit(meta)
	return true


func _retry_meta(attempt: int) -> void:
	if _refused:
		return

	if attempt >= _META_ATTEMPTS:
		push_warning("MapSource: /meta failed %d times; there will be no map this session" % attempt)
		return

	await get_tree().create_timer(_META_RETRY_SECONDS).timeout

	# Another attempt may have landed - or the ticket been refused - while this one was waiting.
	if meta.is_empty() and not _refused:
		_fetch_meta_once(attempt + 1)


func _pump() -> void:
	while not _queue.is_empty() and not _idle.is_empty():
		var id: String = _queue.pop_front()
		var request: HTTPRequest = _idle.pop_back()
		_in_flight[id] = request

		var kind := id.substr(0, 1)
		var url := "%s/%s/%s%s" % [_map_base(), kind, id.substr(2), _SUFFIXES[kind]]

		var handler := func(
			result: int, code: int, headers: PackedStringArray, body: PackedByteArray
		) -> void:
			_on_completed(id, request, result, code, headers, body)

		# Connected per request and disconnected on completion: the node is reused, and a signal left
		# connected would deliver the next tile to every closure that had ever used this node.
		request.request_completed.connect(handler, CONNECT_ONE_SHOT)

		var headers := _headers()
		if kind == _WANT_SERVICES and _service_etags.has(id.substr(2)):
			headers.append("If-None-Match: " + str(_service_etags[id.substr(2)]))

		if request.request(url, headers) != OK:
			request.request_completed.disconnect(handler)
			_back_off(id)
			_release(id, request)


## Everything both kinds of answer have in common - which is every failure mode, because the credential, the
## fog and the backoff are properties of the request rather than of what was asked for.
func _on_completed(
	id: String,
	request: HTTPRequest,
	result: int,
	code: int,
	headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_release(id, request)

	var key := id.substr(2)
	var kind := id.substr(0, 1)

	if result != HTTPRequest.RESULT_SUCCESS:
		# A transport failure, not an answer. Backed off rather than simply left uncached: the next attempt
		# would otherwise be the next frame. See _RETRY_AFTER_SECONDS.
		_back_off(id)
		return

	if code == 404:
		# Uncharted ground, which is most of the world. Remembered so panning over it is not a request
		# storm, and forgotten as soon as the player's charts change.
		if _absent.size() < _MAX_ABSENT:
			_absent[id] = true
		if kind == _WANT_TILE:
			tile_absent.emit(key)
		return

	if code == 401:
		_refuse()
		return

	if code == 304 and kind == _WANT_SERVICES:
		# Nothing changed since the ETag we sent, which is the ordinary answer: only the time is news.
		_services_checked[key] = Time.get_ticks_msec()
		return

	if code != 200:
		push_warning("MapSource: %s returned %s" % [id, code])
		_back_off(id)
		return

	match kind:
		_WANT_TILE:
			_accept_tile(key, headers, body)
		_WANT_PLACES:
			_accept_places(key, body)
		_WANT_SERVICES:
			_accept_services(key, headers, body)
		_WANT_HABITAT:
			_accept_habitat(key, body)


func _accept_tile(key: String, headers: PackedStringArray, body: PackedByteArray) -> void:
	var image := Image.new()
	if image.load_png_from_buffer(body) != OK:
		push_warning("MapSource: tile %s was not a readable PNG" % key)
		_back_off(_WANT_TILE + ":" + key)
		return

	var texture := ImageTexture.create_from_image(image)
	if _is_shared(headers):
		_shared[key] = texture
		_write_through(key, body, _cache_dir, _disk_shared)
	else:
		_personal[key] = texture
		_write_through(key, body, _personal_dir, _disk_personal)

	tile_ready.emit(key, texture)


## Dropped when it belongs to a species the map no longer shows: the player switched while it was in flight.
func _accept_habitat(key: String, body: PackedByteArray) -> void:
	var tile_key := key.substr(key.find("/") + 1)
	if key != _habitat_key(tile_key):
		return

	var image := Image.new()
	if image.load_png_from_buffer(body) != OK:
		push_warning("MapSource: habitat %s was not a readable PNG" % key)
		_back_off(_WANT_HABITAT + ":" + key)
		return

	_habitat[key] = ImageTexture.create_from_image(image)
	habitat_ready.emit(tile_key)


func _accept_places(key: String, body: PackedByteArray) -> void:
	var json := JSON.new()
	if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_ARRAY:
		push_warning("MapSource: places %s was not an array" % key)
		_back_off(_WANT_PLACES + ":" + key)
		return

	_places[key] = json.data
	places_ready.emit(key, json.data)


func _accept_services(key: String, headers: PackedStringArray, body: PackedByteArray) -> void:
	var json := JSON.new()
	if (
		json.parse(body.get_string_from_utf8()) != OK
		or typeof(json.data) != TYPE_DICTIONARY
		or typeof(json.data.get("services")) != TYPE_ARRAY
	):
		push_warning("MapSource: services %s was not an object with a services array" % key)
		_back_off(_WANT_SERVICES + ":" + key)
		return

	# Entries that are not objects are dropped here rather than in every reader: the view draws these every
	# frame, and one malformed entry would otherwise be an error per frame rather than one missing icon.
	var services: Array = []
	for entry in json.data["services"]:
		if typeof(entry) == TYPE_DICTIONARY:
			services.append(entry)

	_services[key] = services
	_services_checked[key] = Time.get_ticks_msec()

	var etag := _header(headers, "etag")
	if etag.is_empty():
		_service_etags.erase(key)
	else:
		_service_etags[key] = etag

	services_ready.emit(key, services)


## Drops every tile's services, their ETags and when they were checked.
##
## Together, always: an ETag kept past the answer it belongs to would have the server say "unchanged" about
## something this no longer holds, and the tile would stay empty until it really did change.
func _forget_services() -> void:
	_services.clear()
	_service_etags.clear()
	_services_checked.clear()


## The value of the response header [param wanted] (lower case), or "" when it was not sent.
static func _header(headers: PackedStringArray, wanted: String) -> String:
	for header in headers:
		var colon := header.find(":")
		if colon > 0 and header.substr(0, colon).strip_edges().to_lower() == wanted:
			return header.substr(colon + 1).strip_edges()
	return ""


## Whether the server said this picture is the same for everybody.
##
## Read off its own `Cache-Control` rather than guessed: the server already distinguishes a fully charted
## tile (`public, immutable`) from one masked to this player (`private`), and that is exactly the
## distinction between which directory a tile belongs in - one that outlives every chart set, or one that is
## thrown away with the charts that produced it.
func _is_shared(headers: PackedStringArray) -> bool:
	for header in headers:
		var lower := header.to_lower()
		if lower.begins_with("cache-control:") and lower.contains("immutable"):
			return true
	return false


## Stops asking, once.
##
## Everything already queued is dropped rather than left to drain: they carry the same ticket and would each
## come back with the same refusal, which is the request storm this exists to end.
func _refuse() -> void:
	if _refused:
		return

	_refused = true
	_queue.clear()
	push_warning("MapSource: the map server refused this session's ticket; asking for nothing further")
	map_unavailable.emit()


func _back_off(key: String) -> void:
	if _retry_after.size() < _MAX_RETRY_AFTER or _retry_after.has(key):
		_retry_after[key] = Time.get_ticks_msec() + int(_RETRY_AFTER_SECONDS * 1000.0)


func _release(key: String, request: HTTPRequest) -> void:
	_in_flight.erase(key)
	_idle.append(request)
	_pump()


## Points this at a world, and reads what a previous session left behind for it.
##
## Persisting is safe in a way the terrain cache is not, which is worth being precise about:
## [code]ClientChunkStore[/code] refuses to persist because it is keyed on a revision the server forgets
## when it restarts, whereas worldMapVersion is derived from the seed and the persisted world row and so
## means the same thing across restarts.
func _use_version(version: String) -> void:
	if version.is_empty():
		return

	var dir := _CACHE_ROOT + version + "/"
	if dir == _cache_dir:
		return

	_cache_dir = dir
	_shared.clear()
	_personal.clear()
	_places.clear()
	_forget_services()
	_absent.clear()
	_retry_after.clear()
	DirAccess.make_dir_recursive_absolute(dir)

	_disk_shared = _index_tiles(dir)
	_use_chart_set()


func _use_chart_set() -> void:
	_disk_personal = {}
	_personal_dir = ""

	if _cache_dir.is_empty() or _chart_key.is_empty():
		return

	_personal_dir = _cache_dir + _chart_key + "/"
	DirAccess.make_dir_recursive_absolute(_personal_dir)

	# The moment a new chart set exists, every tile under every other one is unreachable - so keeping them
	# would cost disk for no possible benefit, and a player who surveys often would otherwise accumulate one
	# directory per survey forever.
	_discard_other_chart_sets()
	_disk_personal = _index_tiles(_personal_dir)


func _discard_other_chart_sets() -> void:
	var dir := DirAccess.open(_cache_dir)
	if dir == null:
		return

	for entry in dir.get_directories():
		if entry != _chart_key:
			_delete_tree(_cache_dir + entry + "/")


func _delete_tree(path: String) -> void:
	var dir := DirAccess.open(path)
	if dir == null:
		return

	for file in dir.get_files():
		dir.remove(file)
	DirAccess.remove_absolute(path.trim_suffix("/"))


## What a directory holds, as key -> path. Reads names only; nothing is decoded here.
func _index_tiles(dir_path: String) -> Dictionary:
	var index := {}
	var dir := DirAccess.open(dir_path)
	if dir == null:
		return index

	for file in dir.get_files():
		if not file.ends_with(".png"):
			continue

		# "L06_00012_00009.png" -> "6/12/9"
		var parts := file.trim_suffix(".png").split("_")
		if parts.size() != 3:
			continue

		index[key_of(int(parts[0].trim_prefix("L")), int(parts[1]), int(parts[2]))] = dir_path + file

	return index


## Decodes a stored tile into memory, or null when there is none or it will not read.
func _from_disk(key: String) -> Texture2D:
	var shared := _decode(_disk_shared, key)
	if shared != null:
		_shared[key] = shared
		return shared

	var personal := _decode(_disk_personal, key)
	if personal != null:
		_personal[key] = personal
		return personal

	return null


func _decode(index: Dictionary, key: String) -> Texture2D:
	if not index.has(key):
		return null

	# Dropped from the index whatever happens: a file that will not decode must not be retried on every
	# frame that draws this tile, and one that does has just been promoted into memory.
	var path: String = index[key]
	index.erase(key)

	var image := Image.new()
	if image.load(path) != OK:
		return null

	return ImageTexture.create_from_image(image)


func _write_through(key: String, png: PackedByteArray, dir_path: String, index: Dictionary) -> void:
	if dir_path.is_empty():
		return

	var parts := key.split("/")
	var path := "%sL%02d_%06d_%06d.png" % [dir_path, int(parts[0]), int(parts[1]), int(parts[2])]
	var file := FileAccess.open(path, FileAccess.WRITE)
	if file == null:
		return

	file.store_buffer(png)
	file.close()
	index.erase(key)


## The selected master's map on the api-server. The master is in the path, as for the companion app, and the
## api-server checks it is the ticket's account's.
func _map_base() -> String:
	return "%s/v1/companion/masters/%d/map" % [SettingsManager.get_instance().api_server_url, _master_id()]


## The selected master's id, or -1 before one is selected.
func _master_id() -> int:
	var master = ConnectionManager.selected_master_info
	return -1 if master == null else int(master.MasterId)


func _headers() -> PackedStringArray:
	return PackedStringArray(["Authorization: Ticket " + ConnectionManager.http_ticket()])
