extends Node
## Guild emblems by the hash that names them: in memory, then on disk under [constant CACHE_DIR], then asked of
## the api-server once. A hash names one picture forever, so no copy is ever checked again.
##
## An autoload, because name tags and the guild window both draw emblems and either may ask first.

## Emitted once the emblem named [param hash] is here; [method texture_of] then has it.
signal emblem_ready(hash: String)

const CACHE_DIR := "user://emblems/"
const _PARALLEL_REQUESTS := 2

var _textures: Dictionary[String, ImageTexture] = {}
var _queued: Array[String] = []
## Asked or queued; also hashes that failed, which are not asked again until the next session.
var _asked: Dictionary[String, bool] = {}
var _idle: Array[HTTPRequest] = []


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute(CACHE_DIR)
	for i in _PARALLEL_REQUESTS:
		var request := HTTPRequest.new()
		request.request_completed.connect(_on_completed.bind(request))
		add_child(request)
		_idle.append(request)


## The emblem, or null while it is being fetched - wait for [signal emblem_ready]. Null for an empty hash.
func texture_of(hash: String) -> ImageTexture:
	if hash.is_empty() or not _is_hash(hash):
		return null
	if _textures.has(hash):
		return _textures[hash]

	var from_disk := _load(_path_of(hash))
	if from_disk != null:
		_textures[hash] = from_disk
		return from_disk

	if not _asked.has(hash):
		_asked[hash] = true
		_queued.append(hash)
	# Also here, not only when a request ends: the ticket arrives after the first name tags ask.
	_pump()
	return null


func _pump() -> void:
	while not _queued.is_empty() and not _idle.is_empty() and ConnectionManager.http_ticket() != "":
		var hash: String = _queued.pop_front()
		var request: HTTPRequest = _idle.pop_back()
		request.set_meta("hash", hash)
		var url := "%s/v1/companion/emblems/%s.png" % [SettingsManager.get_instance().api_server_url, hash]
		var headers := PackedStringArray(["Authorization: Ticket " + ConnectionManager.http_ticket()])
		if request.request(url, headers) != OK:
			_idle.append(request)


func _on_completed(result: int, code: int, _headers: PackedStringArray, body: PackedByteArray, request: HTTPRequest) -> void:
	var hash: String = request.get_meta("hash")
	_idle.append(request)

	if result == HTTPRequest.RESULT_SUCCESS and code == 200:
		var texture := EmblemPicture.texture_of(body)
		if texture != null:
			FileAccess.open(_path_of(hash), FileAccess.WRITE).store_buffer(body)
			_textures[hash] = texture
			emblem_ready.emit(hash)
	_pump()


func _load(path: String) -> ImageTexture:
	if not FileAccess.file_exists(path):
		return null
	return EmblemPicture.texture_of(FileAccess.get_file_as_bytes(path))


func _path_of(hash: String) -> String:
	return CACHE_DIR + hash + ".png"


## Keeps a hash from the server from naming a path outside [constant CACHE_DIR].
func _is_hash(hash: String) -> bool:
	if hash.length() != 64:
		return false
	for c in hash:
		if not "0123456789abcdef".contains(c):
			return false
	return true
