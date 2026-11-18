class_name TextSource
extends Node

## The written texts of this world by their id: in memory, then on disk under [constant CACHE_DIR], then asked of
## the api-server once. The server sends only the id with a [code]TextRef[/code]; the body comes from here, in the
## player's language.
##
## One node in [code]Game.tscn[/code], found with [method get_instance], like [EmblemCache].

const CACHE_DIR := "user://texts/"
const _PARALLEL_REQUESTS := 2
const _GROUP := "text_source"
const _ID_LENGTH := 32

var _bodies: Dictionary[String, String] = {}
## Who waits for a body, by text id: the reference and the callable that shows it.
var _waiting: Dictionary[String, Array] = {}
var _queued: Array[String] = []
## Asked or queued; also ids that failed, which are not asked again while this world lasts.
var _asked: Dictionary[String, bool] = {}
var _idle: Array[HTTPRequest] = []


## The one in the world, or null outside it.
static func get_instance() -> TextSource:
	return (Engine.get_main_loop() as SceneTree).get_first_node_in_group(_GROUP) as TextSource


## Shows a [code]TextRef[/code] through [param show], which takes the finished text. The template is shown at
## once, so the player never waits. When the reference names a written text, [param show] is called a second time
## with that text once it is here.
static func show_ref(ref, show: Callable) -> void:
	var source := get_instance()
	var body := "" if source == null else source._body_of(ref.TextId)
	if not body.is_empty():
		show.call(DialogText.resolve_body(body, ref))
		return

	show.call(DialogText.resolve_ref(ref))
	if source != null and source._is_id(ref.TextId):
		source._wait_for(ref, show)


func _enter_tree() -> void:
	add_to_group(_GROUP)


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute(_locale_dir())
	for i in _PARALLEL_REQUESTS:
		var request := HTTPRequest.new()
		request.request_completed.connect(_on_completed.bind(request))
		add_child(request)
		_idle.append(request)


func _body_of(text_id: String) -> String:
	if not _is_id(text_id):
		return ""
	if _bodies.has(text_id):
		return _bodies[text_id]

	var path := _path_of(text_id)
	if FileAccess.file_exists(path):
		_bodies[text_id] = FileAccess.get_file_as_string(path)
		return _bodies[text_id]

	return ""


func _wait_for(ref, show: Callable) -> void:
	var text_id: String = ref.TextId
	if not _waiting.has(text_id):
		_waiting[text_id] = []
	_waiting[text_id].append({"ref": ref, "show": show})

	if not _asked.has(text_id):
		_asked[text_id] = true
		_queued.append(text_id)
	_pump()


func _pump() -> void:
	while not _queued.is_empty() and not _idle.is_empty() and ConnectionManager.http_ticket() != "":
		var text_id: String = _queued.pop_front()
		var request: HTTPRequest = _idle.pop_back()
		request.set_meta("text_id", text_id)
		var url := "%s/v1/companion/texts/%s/%s" % [SettingsManager.get_instance().api_server_url, text_id, _locale()]
		var headers := PackedStringArray(["Authorization: Ticket " + ConnectionManager.http_ticket()])
		if request.request(url, headers) != OK:
			_idle.append(request)


func _on_completed(result: int, code: int, headers: PackedStringArray, body: PackedByteArray, request: HTTPRequest) -> void:
	var text_id: String = request.get_meta("text_id")
	_idle.append(request)

	if result == HTTPRequest.RESULT_SUCCESS and code == 200:
		var text := body.get_string_from_utf8()
		_bodies[text_id] = text
		# An English fallback is replaced once our language is written, so it is not kept past this session.
		if _language_of(headers) == _locale():
			FileAccess.open(_path_of(text_id), FileAccess.WRITE).store_string(text)
		for waiter: Dictionary in _waiting.get(text_id, []):
			# The window that waited may be gone by now.
			if waiter.show.is_valid():
				waiter.show.call(DialogText.resolve_body(text, waiter.ref))
	_waiting.erase(text_id)
	_pump()


func _language_of(headers: PackedStringArray) -> String:
	for header in headers:
		if header.to_lower().begins_with("content-language:"):
			return header.get_slice(":", 1).strip_edges().to_lower()
	return ""


func _locale() -> String:
	return TranslationServer.get_locale().substr(0, 2).to_lower()


func _locale_dir() -> String:
	return CACHE_DIR + _locale() + "/"


func _path_of(text_id: String) -> String:
	return _locale_dir() + text_id + ".txt"


## Keeps an id from the server from naming a path outside [constant CACHE_DIR].
func _is_id(text_id: String) -> bool:
	if text_id.length() != _ID_LENGTH:
		return false
	for c in text_id:
		if not "0123456789abcdef".contains(c):
			return false
	return true
