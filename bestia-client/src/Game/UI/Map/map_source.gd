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
##
## [b]The whole map, not just what was looked at.[/b] Once it knows the world and the charts, this walks every
## charted tile from the coarsest level down to [constant _FILL_FLOOR_LEVEL] in the background and keeps them on
## disk - see [method _next_fill]. That, and the last [code]/meta[/code] kept beside them, is what lets the map
## open with the api-server down.

## A tile arrived, in memory or on disk; [method cached] has it. [param key] is [method key_of]'s form.
signal tile_ready(key: String)

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

## The answer to [method find] for [param text]: place entries like [method places_of]'s, best first.
signal places_found(text: String, places: Array)

## The answer to [method place_detail]: [code]{place, region, services}[/code], or empty on any failure - an
## uncharted place among them.
signal place_detail_ready(place_id: int, detail: Dictionary)

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

## How often [code]/meta[/code] is re-asked once the quick attempts are spent. For the rest of the session, not
## a fixed number of times: an api-server that comes back is a map that comes back.
const _META_PATIENT_SECONDS := 30.0

## How long one [code]/meta[/code] may take before it counts as failed.
##
## Without it a server that is down is not a failure but a wait: Godot on Windows takes 30 s to give up on a
## refused connection (measured), so the map kept on disk would appear half a minute after login. The answer is a
## few hundred bytes the api-server asks the zone for, and a slow one is still taken by the next attempt.
const _META_TIMEOUT_SECONDS := 5.0

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

## The last [code]/meta[/code] that answered, so a session that cannot reach the api-server can still find its
## world's directory and draw what is in it.
const _META_FILE := _CACHE_ROOT + "meta.json"

## How many of the pool's connections [method prefetch] and the fill may hold at once. The rest stay free so a
## view that starts drawing is never queued behind tiles nobody has looked at yet.
const _BACKGROUND_SLOTS := 2

## The finest level the background fill walks the whole charted map down to.
##
## The overlay's opening level ([code]MapOverlay._OPEN_LEVEL[/code]), so the map a player opens is there wherever
## they have charted, with or without the api-server. Not finer, because each level down is four times the tiles:
## a fully charted 128 km world is about 5,300 tiles from here up - some 120 MB at the ~22 KB a tile weighs - and
## would be 16,000 more for level 2 and 250,000 for level 0. Finer tiles are kept as they are looked at, and a
## view draws a coarser one enlarged where it has nothing finer.
const _FILL_FLOOR_LEVEL := 3

const _PNG_SIGNATURE := [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]

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

## Tiles asked for ahead of need, by [method prefetch]. Only drained into free connections once [member _queue]
## is empty, and at most [constant _BACKGROUND_SLOTS] at a time.
var _background: Array[String] = []
var _in_flight: Dictionary = {}

## Tiles the fill has still to visit, as (level, x, y), coarsest first. Drained after [member _background].
var _fill: Array[Vector3i] = []

## Tiles the fill has taken off [member _fill] and is waiting to hear about, as key -> (level, x, y). What arrives
## for one of these is kept on disk only, and carries the walk on to the four tiles under it.
var _fill_waiting: Dictionary = {}

## Whether a timer is already due to resume a fill held up by a backing-off tile, so it is woken once.
var _fill_wake_due: bool = false

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

## Set while [member meta] is the one kept on disk rather than a live answer - the api-server did not answer.
##
## Nothing is requested while it stands, which leaves the disk cache as the whole map. Not a precaution: a tile's
## URL carries no world version, so a server that came back with a regenerated world would have its tiles filed
## under the old world's directory. The next [code]/meta[/code] that answers clears it.
var _offline: bool = false


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
	if not meta.is_empty() or not _may_ask_meta():
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


## Requests the picture of a tile nobody is looking at yet, so it is already here when somebody does.
##
## Same conditions as [method want], but queued behind everything a view asks for and limited to
## [constant _BACKGROUND_SLOTS] connections. Safe against staleness for the reason the disk cache is: shared tiles
## are immutable and personal ones are dropped with the chart set that produced them, which also empties this
## queue - see [method set_chart_signature].
func prefetch(level: int, tx: int, ty: int) -> void:
	if not _may_request():
		return

	var key := key_of(level, tx, ty)
	var id := _WANT_TILE + ":" + key
	if _holds(key) or _absent.has(id) or _in_flight.has(id) or _queue.has(id) or _background.has(id):
		return
	if Time.get_ticks_msec() < int(_retry_after.get(id, 0)):
		return

	_background.append(id)
	_pump()


## Whether the picture of [param key] is here or on disk.
##
## Asked of the indexes rather than of [method cached]: a tile on disk is already as good as here, and decoding a
## screenful of them for a map nobody has opened is the hitch the disk index exists to avoid.
func _holds(key: String) -> bool:
	return _shared.has(key) or _personal.has(key) or _disk_shared.has(key) or _disk_personal.has(key)


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

	# A view wants what a prefetch was only hoping for: it goes to the front of the line.
	_background.erase(id)
	_queue.append(id)
	_pump()


## Whether there is any point sending a request at all.
##
## An empty ticket is the case that used to produce the second kind of 401: the map can be drawn before the
## zone has authenticated the connection, and sending [code]"Ticket "[/code] with nothing after it asks the
## server to refuse us. Not having a credential or a master yet is worth waiting out rather than asking about.
##
## Also false while [member _offline]; [code]/meta[/code] is asked regardless, being what ends it.
func _may_request() -> bool:
	return not _offline and _may_ask_meta()


func _may_ask_meta() -> bool:
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
	_background.clear()
	_personal.clear()
	_habitat.clear()
	_places.clear()
	_forget_services()
	_absent.clear()
	_retry_after.clear()
	_use_chart_set()


func _fetch_meta_once(attempt: int) -> void:
	var request := HTTPRequest.new()
	request.timeout = _META_TIMEOUT_SECONDS
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

	_offline = false
	_use_meta(json.data)

	var file := FileAccess.open(_META_FILE, FileAccess.WRITE)
	if file != null:
		file.store_buffer(body)
		file.close()

	# Whatever waited out the outage - the fill, mostly - may go now.
	_pump()
	return true


## Draws from the [code]/meta[/code] kept by the last session that reached the api-server, if there is one and
## nothing better has arrived. Only the disk cache is read while it stands - see [member _offline].
func _use_stored_meta() -> void:
	if not meta.is_empty() or not FileAccess.file_exists(_META_FILE):
		return

	var stored: Variant = JSON.parse_string(FileAccess.get_file_as_string(_META_FILE))
	if typeof(stored) != TYPE_DICTIONARY:
		return

	push_warning(
		"MapSource: the api-server is not answering; drawing the map cached for %s" % stored.get("worldMapVersion")
	)
	_offline = true
	_use_meta(stored)


func _use_meta(answer: Dictionary) -> void:
	meta = answer
	_use_version(str(meta.get("worldMapVersion", "")))
	meta_ready.emit(meta)


## Asks for the charted places whose names contain [param text]; answered by [signal places_found], an empty
## list on any failure. Outside the pool: a search is one request the player is waiting on, not a tile.
func find(text: String) -> void:
	var request := HTTPRequest.new()
	add_child(request)
	request.request_completed.connect(
		func(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
			request.queue_free()
			var places: Array = []
			if result == HTTPRequest.RESULT_SUCCESS and code == 200:
				var parsed: Variant = JSON.parse_string(body.get_string_from_utf8())
				if parsed is Array:
					places = parsed
			places_found.emit(text, places)
	)

	if request.request(_map_base() + "/find?q=" + text.uri_encode(), _headers()) != OK:
		request.queue_free()
		places_found.emit(text, [])


## Asks for one place, the region it lies in and every standing service it has; see [signal place_detail_ready].
func place_detail(place_id: int) -> void:
	var request := HTTPRequest.new()
	add_child(request)
	request.request_completed.connect(
		func(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
			request.queue_free()
			var detail: Dictionary = {}
			if result == HTTPRequest.RESULT_SUCCESS and code == 200:
				var parsed: Variant = JSON.parse_string(body.get_string_from_utf8())
				if parsed is Dictionary:
					detail = parsed
			place_detail_ready.emit(place_id, detail)
	)

	if request.request("%s/places/%d" % [_map_base(), place_id], _headers()) != OK:
		request.queue_free()
		place_detail_ready.emit(place_id, {})


func _retry_meta(attempt: int) -> void:
	if _refused:
		return

	# At the first failure rather than the last: the stored answer is as good now as it will be in fifteen
	# seconds, and the player opening the map in the meantime is owed what is on disk.
	_use_stored_meta()

	if attempt == _META_ATTEMPTS:
		push_warning(
			"MapSource: /meta failed %d times; asking every %d s from now on" % [attempt, int(_META_PATIENT_SECONDS)]
		)

	var wait := _META_RETRY_SECONDS if attempt < _META_ATTEMPTS else _META_PATIENT_SECONDS
	await get_tree().create_timer(wait).timeout

	# Another attempt may have landed - or the ticket been refused - while this one was waiting.
	if (meta.is_empty() or _offline) and not _refused:
		_fetch_meta_once(attempt + 1)


func _pump() -> void:
	while not _idle.is_empty():
		var id: String
		if not _queue.is_empty():
			id = _queue.pop_front()
		elif _idle.size() > _POOL_SIZE - _BACKGROUND_SLOTS:
			# The ground around the player before the rest of the map: it is what is about to be looked at.
			id = _background.pop_front() if not _background.is_empty() else _next_fill()
			if id.is_empty():
				break
		else:
			break

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


## Starts the walk over the whole charted map from the top of the pyramid, dropping one already under way.
##
## Called whenever what it would find changes - a world, or a chart set - and so never with anything worth keeping
## in [member _fill]: the tiles it would have visited belong to the old one.
func _start_fill() -> void:
	_fill.clear()
	_fill_waiting.clear()
	if meta.is_empty() or _chart_key.is_empty():
		return

	var top := int(meta.get("maxLevel", 0))
	var across := _tiles_across(top)
	for ty in across.y:
		for tx in across.x:
			_fill.append(Vector3i(top, tx, ty))

	_pump()


## The next tile the fill has to ask for, as a request id, or "" when there is none it may ask for now.
##
## Walks the pyramid breadth first, so the coarse levels are complete before the fine ones are begun, and
## descends only under tiles that exist: a 404 is ground with nothing charted in it, and neither is anything
## finer inside it. A tile already on disk costs nothing - the walk goes straight on underneath it - so a
## session after the first one asks only for what the last one did not finish, or what was charted since.
func _next_fill() -> String:
	if not _may_request():
		return ""

	while not _fill.is_empty():
		var tile: Vector3i = _fill[0]
		var key := key_of(tile.x, tile.y, tile.z)
		var id := _WANT_TILE + ":" + key

		if _holds(key):
			_fill.pop_front()
			_fill_below(tile)
			continue

		# Waited out at the head rather than passed over. A backing-off tile is, nearly always, a server that is
		# not answering, and then the one request that keeps finding out whether it is back is the right amount.
		var wait := int(_retry_after.get(id, 0)) - Time.get_ticks_msec()
		if wait > 0:
			_wake_fill(wait)
			return ""

		_fill.pop_front()
		if _absent.has(id):
			continue

		_fill_waiting[key] = tile
		# A view asked for it first. Its answer comes through _accept_tile all the same.
		if _in_flight.has(id):
			continue

		return id

	return ""


## Queues the up to four tiles one level under [param tile], if the fill goes that deep.
func _fill_below(tile: Vector3i) -> void:
	var level := tile.x - 1
	if level < maxi(_FILL_FLOOR_LEVEL, int(meta.get("minLevel", 0))):
		return

	var across := _tiles_across(level)
	for ty in range(tile.z * 2, mini(tile.z * 2 + 2, across.y)):
		for tx in range(tile.y * 2, mini(tile.y * 2 + 2, across.x)):
			_fill.append(Vector3i(level, tx, ty))


## How many tiles the world has across and up at [param level] - the server's [code]TileId.tilesAcross[/code].
func _tiles_across(level: int) -> Vector2i:
	var span := float(meta.get("tileSize", 256)) * pow(2.0, level)
	return Vector2i(
		maxi(ceili(float(meta.get("worldWidthMetres", 0.0)) / span), 1),
		maxi(ceili(float(meta.get("worldHeightMetres", 0.0)) / span), 1)
	)


func _wake_fill(after_msec: int) -> void:
	if _fill_wake_due:
		return

	_fill_wake_due = true
	get_tree().create_timer(after_msec / 1000.0).timeout.connect(func() -> void:
		_fill_wake_due = false
		_pump()
	)


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
			# Nothing under it to walk, either.
			_fill_waiting.erase(key)
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
	var shared := _is_shared(headers)
	var dir_path := _cache_dir if shared else _personal_dir

	if _fill_waiting.has(key):
		# Kept on disk only. The fill is the whole charted map, and a decoded tile is a quarter of a megabyte
		# that may never be looked at this session; a view that does look decodes it from there.
		if body.slice(0, _PNG_SIGNATURE.size()) != PackedByteArray(_PNG_SIGNATURE):
			push_warning("MapSource: tile %s was not a PNG" % key)
			_back_off(_WANT_TILE + ":" + key)
			return

		var tile: Vector3i = _fill_waiting[key]
		_fill_waiting.erase(key)
		var path := _write_through(key, body, dir_path)
		var index: Dictionary = _disk_shared if shared else _disk_personal
		if not path.is_empty():
			index[key] = path

		# Pumped here, not only on release: the connection was freed before this answer was read, and then
		# the tiles under it were not queued yet - a fill that had nothing else in hand would stop at this one.
		_fill_below(tile)
		_pump()
		tile_ready.emit(key)
		return

	var image := Image.new()
	if image.load_png_from_buffer(body) != OK:
		push_warning("MapSource: tile %s was not a readable PNG" % key)
		_back_off(_WANT_TILE + ":" + key)
		return

	var texture := ImageTexture.create_from_image(image)
	if shared:
		_shared[key] = texture
		_disk_shared.erase(key)
	else:
		_personal[key] = texture
		_disk_personal.erase(key)
	_write_through(key, body, dir_path)

	tile_ready.emit(key)


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
	_background.clear()
	_fill.clear()
	_fill_waiting.clear()
	push_warning("MapSource: the map server refused this session's ticket; asking for nothing further")
	map_unavailable.emit()


func _back_off(id: String) -> void:
	var tile: Variant = _fill_waiting.get(id.substr(2)) if id.begins_with(_WANT_TILE + ":") else null

	# The fill's own tile past the cap too: it is one entry, and without it the fill would re-ask at once.
	if _retry_after.size() < _MAX_RETRY_AFTER or _retry_after.has(id) or tile != null:
		_retry_after[id] = Time.get_ticks_msec() + int(_RETRY_AFTER_SECONDS * 1000.0)

	# Back at the head of the walk rather than out of it: dropping it would drop everything charted under it.
	if tile != null:
		_fill_waiting.erase(id.substr(2))
		_fill.push_front(tile)


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
	_background.clear()
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
	_fill.clear()
	_fill_waiting.clear()

	if _cache_dir.is_empty() or _chart_key.is_empty():
		return

	_personal_dir = _cache_dir + _chart_key + "/"
	DirAccess.make_dir_recursive_absolute(_personal_dir)

	# The moment a new chart set exists, every tile under every other one is unreachable - so keeping them
	# would cost disk for no possible benefit, and a player who surveys often would otherwise accumulate one
	# directory per survey forever.
	_discard_other_chart_sets()
	_disk_personal = _index_tiles(_personal_dir)
	_start_fill()


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


## Stores a tile under [param dir_path] and returns where, or "" when it could not be stored.
func _write_through(key: String, png: PackedByteArray, dir_path: String) -> String:
	if dir_path.is_empty():
		return ""

	var parts := key.split("/")
	var path := "%sL%02d_%06d_%06d.png" % [dir_path, int(parts[0]), int(parts[1]), int(parts[2])]
	var file := FileAccess.open(path, FileAccess.WRITE)
	if file == null:
		return ""

	file.store_buffer(png)
	file.close()
	return path


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
