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
## [b]Authentication.[/b] The map ticket the zone hands over in [code]AuthenticationSuccess[/code], which
## lives exactly as long as the connection. Deliberately not the login JWT this used to send: that expires an
## hour after the login server issued it and the socket only checks it once, so the map quietly stopped
## working an hour into every session while the game carried on. See [method ConnectionManager.http_ticket].
##
## [b]What the server never sends.[/b] Nothing tells this when the player's charts change. A chart is an
## item, so the inventory sync already carries the news - see [method set_chart_signature], called from
## [code]ui.gd[/code] whenever the set of charts held changes.

## A tile arrived. [param key] is [method key_of]'s form.
signal tile_ready(key: String, texture: Texture2D)

## The server has no tile there for this player: they have charted none of that ground.
signal tile_absent(key: String)

## World geometry and the level range, from [code]/map/v1/meta[/code]. Nothing can be addressed before it.
signal meta_ready(meta: Dictionary)

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

## Attempts at [code]/map/v1/meta[/code], and the wait between them.
##
## Retried because it can legitimately fail at the moment it is first asked: the client switches to the game
## scene the instant it [i]sends[/i] its master selection, so this can reach the zone before the session it
## identifies itself with exists, and be told so. Nothing else asks again, and without a map version nothing
## can be addressed - so one lost race would mean no map for the whole session.
const _META_ATTEMPTS := 10
const _META_RETRY_SECONDS := 1.5

const _CACHE_ROOT := "user://mapcache/"

## World geometry, empty until [signal meta_ready]. Read it rather than assuming 256 or level 9.
var meta: Dictionary = {}

## Tiles every player sees identically. See [method _is_shared] for how one is recognised.
var _shared: Dictionary = {}

## Tiles masked to this player's charts. Dropped whenever those change - see [method set_chart_signature].
var _personal: Dictionary = {}

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

## Whether the one-off "no master selected yet" line has been logged. A 409 is transient, so the requests keep
## backing off and retrying; only the noise is suppressed.
var _warned_no_master: bool = false


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
	return _absent.has(key)


## Requests [param key] unless it is already known, in flight, known to be absent, or backing off.
func want(level: int, tx: int, ty: int) -> void:
	if not _may_request():
		return

	var key := key_of(level, tx, ty)
	if cached(key) != null or is_absent(key) or _in_flight.has(key) or _queue.has(key):
		return
	if Time.get_ticks_msec() < int(_retry_after.get(key, 0)):
		return

	_queue.append(key)
	_pump()


## Whether there is any point sending a request at all.
##
## An empty ticket is the case that used to produce the second kind of 401: the map can be drawn before the
## zone has authenticated the connection, and sending [code]"Bearer "[/code] with nothing after it asks the
## server to refuse us. Not having a credential yet is worth waiting out rather than asking about.
func _may_request() -> bool:
	return not _refused and not ConnectionManager.http_ticket().is_empty()


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

	var err := request.request(_base() + "/map/v1/meta", _headers())
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
		var key: String = _queue.pop_front()
		var request: HTTPRequest = _idle.pop_back()
		_in_flight[key] = request

		var parts := key.split("/")
		var url := "%s/map/v1/t/%s/%s/%s.png" % [_base(), parts[0], parts[1], parts[2]]

		var handler := func(
			result: int, code: int, headers: PackedStringArray, body: PackedByteArray
		) -> void:
			_on_tile_completed(key, request, result, code, headers, body)

		# Connected per request and disconnected on completion: the node is reused, and a signal left
		# connected would deliver the next tile to every closure that had ever used this node.
		request.request_completed.connect(handler, CONNECT_ONE_SHOT)

		if request.request(url, _headers()) != OK:
			request.request_completed.disconnect(handler)
			_back_off(key)
			_release(key, request)


func _on_tile_completed(
	key: String,
	request: HTTPRequest,
	result: int,
	code: int,
	headers: PackedStringArray,
	body: PackedByteArray
) -> void:
	_release(key, request)

	if result != HTTPRequest.RESULT_SUCCESS:
		# A transport failure, not an answer. Backed off rather than simply left uncached: the next attempt
		# would otherwise be the next frame. See _RETRY_AFTER_SECONDS.
		_back_off(key)
		return

	if code == 404:
		# Uncharted ground, which is most of the world. Remembered so panning over it is not a request
		# storm, and forgotten as soon as the player's charts change.
		if _absent.size() < _MAX_ABSENT:
			_absent[key] = true
		tile_absent.emit(key)
		return

	if code == 401:
		_refuse()
		return

	if code == 409:
		# The session exists but has no master yet, which resolves itself - so this keeps retrying and only
		# stops saying so.
		if not _warned_no_master:
			_warned_no_master = true
			push_warning("MapSource: the zone has no master selected for this account yet; still trying")
		_back_off(key)
		return

	if code != 200:
		push_warning("MapSource: tile %s returned %s" % [key, code])
		_back_off(key)
		return

	var image := Image.new()
	if image.load_png_from_buffer(body) != OK:
		push_warning("MapSource: tile %s was not a readable PNG" % key)
		_back_off(key)
		return

	var texture := ImageTexture.create_from_image(image)
	if _is_shared(headers):
		_shared[key] = texture
		_write_through(key, body, _cache_dir, _disk_shared)
	else:
		_personal[key] = texture
		_write_through(key, body, _personal_dir, _disk_personal)

	tile_ready.emit(key, texture)


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


func _base() -> String:
	return SettingsManager.map_server_url


func _headers() -> PackedStringArray:
	return PackedStringArray(["Authorization: Bearer " + ConnectionManager.http_ticket()])
