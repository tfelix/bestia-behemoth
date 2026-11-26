class_name CrashLogger extends Logger
## Passes Godot's errors - script errors, engine errors and C# exceptions in Godot callbacks - to [CrashReports].
## Godot calls a logger from any thread, so this only queues, under a lock, and [method take] runs on the main thread.

## Enough for one bad frame; the rest of an error flood adds nothing.
const MAX_PENDING := 50

var _mutex := Mutex.new()
var _pending: Array[Dictionary] = []


func _log_error(function: String, file: String, line: int, code: String, rationale: String, _editor_notify: bool,
		error_type: int, script_backtraces: Array[ScriptBacktrace]) -> void:
	if error_type == ERROR_TYPE_WARNING:
		return

	var headline := rationale if not rationale.is_empty() else code
	var text := "%s\n  at %s (%s:%d)" % [headline, function, file, line]
	for backtrace in script_backtraces:
		text += "\n" + backtrace.format()

	_mutex.lock()
	if _pending.size() < MAX_PENDING:
		_pending.append({"signature": _signature(error_type, function, file, line, code), "text": text})
	_mutex.unlock()


func take() -> Array[Dictionary]:
	_mutex.lock()
	var taken := _pending.duplicate()
	_pending.clear()
	_mutex.unlock()
	return taken


## Where the error was raised and what it says, with the numbers taken out: an entity id or a count in the
## message must not split one bug into many issues.
static func _signature(error_type: int, function: String, file: String, line: int, code: String) -> String:
	var digits := RegEx.create_from_string("\\d+")
	var key := "%d|%s|%s:%d|%s" % [error_type, function, file, line, digits.sub(code, "#", true)]
	return key.sha256_text()
