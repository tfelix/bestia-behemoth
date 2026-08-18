class_name MapSource
extends Node

## Fetches map tiles over HTTP and remembers them.
##
## One of these is shared by every view of the map - the minimap and the full overlay look at the same
## world through the same cache, so panning the overlay warms the minimap and the other way round.
##
## [b]Why HTTP and not the game socket.[/b] A screen of map is a few hundred kilobytes of PNG that gets
## re-requested every time the window opens. Conditional requests, a cache that survives a restart and a
## transfer that cannot delay a movement packet are all things HTTP already does.
##
## [b]Authentication.[/b] The login JWT this session was opened with, which the zone verifies with the same
## validator the socket handshake uses. No ticket message, no second expiry to keep in step.
##
## [b]What the server never sends.[/b] Nothing tells this when the player's charts change. A chart is an
## item, so the inventory sync already carries the news - see [method invalidate_coverage], called from
## [code]ui.gd[/code] when the set of charts held changes.

## A tile arrived. [param key] is [method key_of]'s form.
signal tile_ready(key: String, texture: Texture2D)

## The server has no tile there for this player: they have charted none of that ground.
signal tile_absent(key: String)

## World geometry and the level range, from [code]/map/v1/meta[/code]. Nothing can be addressed before it.
signal meta_ready(meta: Dictionary)

## Concurrent requests. Godot's HTTPRequest is one-at-a-time per node, so this is literally a node count.
## Six is about a screen of tiles in two waves without opening a connection per tile.
const _POOL_SIZE := 6

## Absent tiles are the common case - most of the world is uncharted - so remembering them matters as much
## as remembering the images. Capped because a player panning across an empty world would otherwise
## accumulate one entry per tile of it.
const _MAX_ABSENT := 4096

const _CACHE_ROOT := "user://mapcache/"

## World geometry, empty until [signal meta_ready]. Read it rather than assuming 256 or level 9.
var meta: Dictionary = {}

## Tiles every player sees identically, so they are safe on disk and survive a restart. Keyed by
## [method key_of]. See [method _is_shared] for how one is recognised.
var _shared: Dictionary = {}

## Tiles masked to this player's charts, plus the absent markers. Memory only, and dropped whenever the
## charts change - which is what [method invalidate_coverage] is.
var _personal: Dictionary = {}

var _absent: Dictionary = {}

var _idle: Array[HTTPRequest] = []
var _queue: Array[String] = []
var _in_flight: Dictionary = {}

## Directory the shared tiles live in, or "" until the world version is known. Keyed by the server's
## worldMapVersion, so a regenerated world or a restyled map simply stops looking at the old directory
## instead of serving stale pictures out of it.
var _cache_dir: String = ""


static func key_of(level: int, tx: int, ty: int) -> String:
	return "%d/%d/%d" % [level, tx, ty]


func _ready() -> void:
	for i in _POOL_SIZE:
		var request := HTTPRequest.new()
		request.name = "TileRequest%d" % i
		add_child(request)
		_idle.append(request)


## Asks for the world geometry. Safe to call more than once; the answer is idempotent.
func fetch_meta() -> void:
	var request := HTTPRequest.new()
	add_child(request)
	request.request_completed.connect(
		func(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
			request.queue_free()
			if result != HTTPRequest.RESULT_SUCCESS or code != 200:
				push_warning("MapSource: /meta failed (result=%s, code=%s)" % [result, code])
				return

			var json := JSON.new()
			if json.parse(body.get_string_from_utf8()) != OK or typeof(json.data) != TYPE_DICTIONARY:
				push_warning("MapSource: /meta was not an object")
				return

			meta = json.data
			_use_version(str(meta.get("worldMapVersion", "")))
			meta_ready.emit(meta)
	)

	var err := request.request(_base() + "/map/v1/meta", _headers())
	if err != OK:
		push_warning("MapSource: could not start /meta request: %s" % err)
		request.queue_free()


## The tile if it is already here, else null. Never blocks and never requests - ask [method want] for that.
func cached(key: String) -> Texture2D:
	if _shared.has(key):
		return _shared[key]
	if _personal.has(key):
		return _personal[key]
	return null


func is_absent(key: String) -> bool:
	return _absent.has(key)


## Requests [param key] unless it is already known, in flight, or known to be absent.
func want(level: int, tx: int, ty: int) -> void:
	var key := key_of(level, tx, ty)
	if cached(key) != null or is_absent(key) or _in_flight.has(key) or _queue.has(key):
		return

	_queue.append(key)
	_pump()


## Forgets everything that depended on which charts the player was holding.
##
## The shared tiles are deliberately kept. A fully charted tile is the same picture for everyone and does
## not become a different one when its owner charts more land - so charting does not throw away what was
## already complete, which is most of a well-travelled player's map.
func invalidate_coverage() -> void:
	_personal.clear()
	_absent.clear()


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
		# A transport failure, not an answer. Left uncached so the next pan tries again.
		return

	if code == 404:
		# Uncharted ground, which is most of the world. Remembered so panning over it is not a request
		# storm, and forgotten as soon as the player's charts change.
		if _absent.size() < _MAX_ABSENT:
			_absent[key] = true
		tile_absent.emit(key)
		return

	if code != 200:
		push_warning("MapSource: tile %s returned %s" % [key, code])
		return

	var image := Image.new()
	if image.load_png_from_buffer(body) != OK:
		push_warning("MapSource: tile %s was not a readable PNG" % key)
		return

	var texture := ImageTexture.create_from_image(image)
	if _is_shared(headers):
		_shared[key] = texture
		_write_through(key, body)
	else:
		_personal[key] = texture

	tile_ready.emit(key, texture)


## Whether the server said this picture is the same for everybody.
##
## Read off its own `Cache-Control` rather than guessed: the server already distinguishes a fully charted
## tile (`public, immutable`) from one masked to this player (`private`), and that is exactly the
## distinction between what may be written to disk and what may not.
func _is_shared(headers: PackedStringArray) -> bool:
	for header in headers:
		var lower := header.to_lower()
		if lower.begins_with("cache-control:") and lower.contains("immutable"):
			return true
	return false


func _release(key: String, request: HTTPRequest) -> void:
	_in_flight.erase(key)
	_idle.append(request)
	_pump()


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
	DirAccess.make_dir_recursive_absolute(dir)
	_load_from_disk()


## Reads whatever a previous session left behind.
##
## Safe in a way the terrain cache is not, which is worth being precise about: `ClientChunkStore` refuses
## to persist because it is keyed on a revision the server forgets when it restarts, whereas
## worldMapVersion is derived from the seed and the persisted world row and so means the same thing across
## restarts.
func _load_from_disk() -> void:
	var dir := DirAccess.open(_cache_dir)
	if dir == null:
		return

	for file in dir.get_files():
		if not file.ends_with(".png"):
			continue

		var image := Image.new()
		if image.load(_cache_dir + file) != OK:
			continue

		# "L06_00012_00009.png" -> "6/12/9"
		var parts := file.trim_suffix(".png").split("_")
		if parts.size() != 3:
			continue

		var key := key_of(
			int(parts[0].trim_prefix("L")), int(parts[1]), int(parts[2])
		)
		_shared[key] = ImageTexture.create_from_image(image)


func _write_through(key: String, png: PackedByteArray) -> void:
	if _cache_dir.is_empty():
		return

	var parts := key.split("/")
	var path := "%sL%02d_%06d_%06d.png" % [_cache_dir, int(parts[0]), int(parts[1]), int(parts[2])]
	var file := FileAccess.open(path, FileAccess.WRITE)
	if file == null:
		return

	file.store_buffer(png)
	file.close()


func _base() -> String:
	return SettingsManager.map_server_url


func _headers() -> PackedStringArray:
	return PackedStringArray(["Authorization: Bearer " + ConnectionManager.login_token()])
