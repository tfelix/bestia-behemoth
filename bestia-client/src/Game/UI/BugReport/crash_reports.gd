class_name CrashReports extends Node
## Sends crash reports without asking: unhandled C# exceptions, Godot errors, and a previous session that ended
## without shutting down. Players can turn it off in Settings; the first start says so.
##
## Lives under the tree's root from the main menu on, not in one scene: errors happen in every scene, and the
## queue can only be sent once a later screen has a ticket. Reports wait as files in [constant QUEUE_DIR] until
## then, also across restarts.

const QUEUE_DIR := "user://crash_reports"
const SESSION_MARKER := QUEUE_DIR + "/session.lock"
const LOG_DIR := "user://logs"
const MAX_PER_SESSION := 10
const RETRY_SECONDS := 60.0
const _CRASH_HOOKS := "res://Game/UI/BugReport/CrashHooks.cs"
const _NOTICE := "Bestia sends crash reports automatically while it is in alpha: the error, the game's log and " + \
	"technical data about your computer. Nothing that names you. You can turn this off in Settings > Privacy."

static var _installed := false

var _logger := CrashLogger.new()
var _upload := BugReportUpload.new()
var _seen_signatures := {}
var _queued_this_session := 0
var _sending_path := ""
var _next_send_at := 0.0


## Called by every visit to the main menu; only the first one installs.
static func install(tree: SceneTree) -> void:
	if _installed:
		return
	_installed = true
	tree.root.add_child.call_deferred(CrashReports.new())


func _ready() -> void:
	DirAccess.make_dir_recursive_absolute(QUEUE_DIR)
	if FileAccess.file_exists(SESSION_MARKER):
		_queue_unclean_exit()
	FileAccess.open(SESSION_MARKER, FileAccess.WRITE).store_string(Time.get_datetime_string_from_system())

	OS.add_logger(_logger)
	load(_CRASH_HOOKS).new().Install(ProjectSettings.globalize_path(QUEUE_DIR), SettingsManager.VERSION)
	add_child(_upload)
	_upload.finished.connect(_on_sent)
	_show_notice_once.call_deferred()


## Only a shutdown that gets this far removes the marker, so a crashed session leaves it behind.
func _exit_tree() -> void:
	OS.remove_logger(_logger)
	DirAccess.remove_absolute(SESSION_MARKER)


func _process(_delta: float) -> void:
	for error: Dictionary in _logger.take():
		_queue(error.signature, error.text)
	_send_next()


func _is_enabled() -> bool:
	return SettingsManager.get_instance().send_crash_reports


## Kept with the context of now, which for an error just logged is the moment it happened.
func _queue(signature: String, text: String) -> void:
	if not _is_enabled() or _queued_this_session >= MAX_PER_SESSION or _seen_signatures.has(signature):
		return
	_seen_signatures[signature] = true
	_queued_this_session += 1

	_write_report({
		"signature": signature,
		"text": text,
		"clientVersion": SettingsManager.VERSION,
		"masterId": BugReportContext.master_id(),
		"context": JSON.stringify(BugReportContext.collect(get_viewport()), "  "),
		"log": BugReportContext.log_tail(),
	})


## The previous session left its marker. If the C# hooks wrote down why, that report gets the session's log, which
## is complete only now; otherwise all there is to go on is the log.
func _queue_unclean_exit() -> void:
	var previous_log := BugReportContext.tail_of_file(_previous_log_path())
	for path in _queued_report_paths():
		var report = JSON.parse_string(FileAccess.get_file_as_string(path))
		if report is Dictionary and report.get("fatal", false) and not report.has("log"):
			report["log"] = previous_log
			FileAccess.open(path, FileAccess.WRITE).store_string(JSON.stringify(report))
			return

	if not _is_enabled():
		return
	var last_error := _last_error_line(previous_log)
	_write_report({
		"signature": ("unclean-exit|" + RegEx.create_from_string("\\d+").sub(last_error, "#", true)).sha256_text(),
		"text": "The previous session ended without shutting down.\nLast error in its log: %s" % last_error,
		"clientVersion": SettingsManager.VERSION,
		"log": previous_log,
	})


## Godot renames the last session's godot.log when this one starts; the newest renamed file is it.
func _previous_log_path() -> String:
	var rotated := Array(DirAccess.get_files_at(LOG_DIR)).filter(
		func(file: String) -> bool: return file.begins_with("godot") and file != "godot.log")
	rotated.sort()
	return LOG_DIR + "/" + rotated.back() if not rotated.is_empty() else ""


func _last_error_line(log_text: String) -> String:
	var lines := log_text.split("\n")
	for i in range(lines.size() - 1, -1, -1):
		var line := lines[i]
		if line.contains("ERROR") or line.contains("Exception") or line.contains("crashed"):
			return line.strip_edges()
	return "none"


func _write_report(report: Dictionary) -> void:
	var name := "%d-%s.json" % [int(Time.get_unix_time_from_system() * 1000), str(report.signature).left(12)]
	FileAccess.open(QUEUE_DIR + "/" + name, FileAccess.WRITE).store_string(JSON.stringify(report))


func _queued_report_paths() -> Array:
	var names := Array(DirAccess.get_files_at(QUEUE_DIR)).filter(func(file: String) -> bool: return file.ends_with(".json"))
	names.sort()
	return names.map(func(file: String) -> String: return QUEUE_DIR + "/" + file)


## One report at a time, oldest first; a player who turned reports off has the queue dropped.
func _send_next() -> void:
	if _sending_path != "" or Time.get_ticks_msec() / 1000.0 < _next_send_at:
		return
	var paths := _queued_report_paths()
	if paths.is_empty():
		return
	if not _is_enabled():
		for path: String in paths:
			DirAccess.remove_absolute(path)
		return
	if ConnectionManager.http_ticket() == "" or _upload.is_busy():
		return

	var path: String = paths[0]
	var report = JSON.parse_string(FileAccess.get_file_as_string(path))
	if not (report is Dictionary) or not report.has("signature"):
		DirAccess.remove_absolute(path)
		return

	var fields := {
		"text": BugReportScrubber.scrub(str(report.get("text", "crash"))),
		"kind": "crash",
		"signature": report.signature,
		"clientVersion": report.get("clientVersion", SettingsManager.VERSION),
	}
	if int(report.get("masterId", 0)) > 0:
		fields["masterId"] = int(report.masterId)
	var files := {
		"context": BugReportScrubber.scrub(str(report.get("context", ""))).to_utf8_buffer(),
		"log": BugReportScrubber.scrub(str(report.get("log", ""))).to_utf8_buffer(),
	}
	if _upload.send(fields, files):
		_sending_path = path


func _on_sent(accepted: bool, status: int) -> void:
	# A report the server refuses as malformed or too large would be refused forever.
	if accepted or status == 400 or status == 413:
		DirAccess.remove_absolute(_sending_path)
	else:
		_next_send_at = Time.get_ticks_msec() / 1000.0 + RETRY_SECONDS
	_sending_path = ""


func _show_notice_once() -> void:
	var settings := SettingsManager.get_instance()
	if settings.crash_report_notice_shown:
		return
	settings.crash_report_notice_shown = true
	SettingsManager.save()

	var notice := AcceptDialog.new()
	notice.title = "Crash reports"
	notice.dialog_text = _NOTICE
	notice.dialog_autowrap = true
	notice.min_size = Vector2i(460, 0)
	notice.confirmed.connect(notice.queue_free)
	notice.canceled.connect(notice.queue_free)
	add_child(notice)
	notice.popup_centered()
