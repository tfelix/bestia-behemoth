class_name BugReport extends Control
## The alpha's bug button in the bottom right corner, and the report dialog it opens. Placed in every scene a
## signed-in player can report from: master select, master creation and the game HUD, where the ESC menu opens
## it too.

const MAX_SCREENSHOT_WIDTH := 1920
const JPEG_QUALITY := 0.85
## The api-server takes up to 4 MiB of chunks; one chunk around the camera each way stays well below that.
const CHUNK_DUMP_RADIUS := 1
const MAX_CHUNK_DUMP_BYTES := 4 * 1024 * 1024
const TOAST_SECONDS := 4.0
const _TERRAIN_SCRIPT := "res://Game/World/TerrainRenderer.cs"
const _NET_LOG_TAIL := "res://Bnet/NetLogTail.cs"

@onready var _button: Button = $BugButton
@onready var _dialog: Control = $Dialog
@onready var _text: TextEdit = %ReportText
@onready var _attach: CheckBox = %AttachScreenshot
@onready var _thumbnail: TextureRect = %Thumbnail
@onready var _status: Label = %Status
@onready var _send: Button = %SendButton
@onready var _toast: Label = $Toast
@onready var _upload: BugReportUpload = $Upload

var _screenshot: Image = null


func _ready() -> void:
	_dialog.visible = false
	_toast.visible = false
	_button.add_to_group("world_blocking_ui")
	_dialog.add_to_group("world_blocking_ui")
	_button.pressed.connect(open_report)
	%CancelButton.pressed.connect(_close)
	_send.pressed.connect(_on_send_pressed)
	_upload.finished.connect(_on_upload_finished)
	_style()


func _input(event: InputEvent) -> void:
	if _dialog.visible and event.is_action_pressed("ui_cancel"):
		_close()
		get_viewport().set_input_as_handled()


## Takes the screenshot from the next drawn frame, so neither this button nor a menu closed just before is in it.
func open_report() -> void:
	if _dialog.visible:
		return

	_button.visible = false
	await RenderingServer.frame_post_draw
	_screenshot = _capture()
	_button.visible = true

	_thumbnail.texture = ImageTexture.create_from_image(_screenshot)
	_attach.button_pressed = true
	_status.text = ""
	_send.disabled = false
	_dialog.visible = true
	_text.grab_focus()


func _close() -> void:
	_dialog.visible = false
	_screenshot = null


func _capture() -> Image:
	var image := get_viewport().get_texture().get_image()
	if image.get_width() > MAX_SCREENSHOT_WIDTH:
		var height := int(image.get_height() * float(MAX_SCREENSHOT_WIDTH) / image.get_width())
		image.resize(MAX_SCREENSHOT_WIDTH, height, Image.INTERPOLATE_BILINEAR)
	return image


func _on_send_pressed() -> void:
	var text := _text.text.strip_edges()
	if text.is_empty():
		_status.text = "Please describe what went wrong."
		return

	var fields := {"text": text, "kind": "manual", "clientVersion": SettingsManager.VERSION}
	var master_id := BugReportContext.master_id()
	if master_id > 0:
		fields["masterId"] = master_id

	var files := {
		"context": BugReportScrubber.scrub(JSON.stringify(BugReportContext.collect(get_viewport()), "  ")).to_utf8_buffer(),
		"log": BugReportScrubber.scrub(BugReportContext.log_tail()).to_utf8_buffer(),
		"netlog": BugReportScrubber.scrub(load(_NET_LOG_TAIL).new().Text()).to_utf8_buffer(),
		"chunks": _chunk_dump(),
	}
	if _attach.button_pressed and _screenshot != null:
		files["screenshot"] = _screenshot.save_jpg_to_buffer(JPEG_QUALITY)

	if not _upload.send(fields, files):
		_status.text = "Not connected to the server, or a report is still being sent."
		return

	_send.disabled = true
	_status.text = "Sending..."


func _on_upload_finished(accepted: bool, status: int) -> void:
	if accepted:
		_close()
		_text.text = ""
		_show_toast("Thank you! Your report was sent.")
		return

	_send.disabled = false
	if status == 429:
		_status.text = "You sent many reports lately. Please try again later."
	else:
		_status.text = "Sending failed (%d). Please try again." % status


func _show_toast(message: String) -> void:
	_toast.text = message
	_toast.visible = true
	await get_tree().create_timer(TOAST_SECONDS).timeout
	_toast.visible = false


## The streamed chunks around the camera as a dev harness reads them, or nothing outside the world.
func _chunk_dump() -> PackedByteArray:
	var terrain := _find_terrain()
	var camera := get_viewport().get_camera_3d()
	if terrain == null or camera == null:
		return PackedByteArray()

	var path: String = terrain.call("DumpChunks", camera, CHUNK_DUMP_RADIUS)
	if path.is_empty():
		return PackedByteArray()

	var bytes := FileAccess.get_file_as_bytes(path)
	DirAccess.remove_absolute(path)
	return bytes if bytes.size() <= MAX_CHUNK_DUMP_BYTES else PackedByteArray()


func _find_terrain() -> Node:
	for node in get_tree().root.find_children("*", "Node3D", true, false):
		var script: Script = node.get_script()
		if script != null and script.resource_path == _TERRAIN_SCRIPT:
			return node
	return null


## Dresses the dialog like the ESC menu; the scene only places the nodes.
func _style() -> void:
	var panel_style := StyleBoxFlat.new()
	panel_style.bg_color = Color(0.09, 0.09, 0.1, 0.96)
	panel_style.border_color = Color(0.85, 0.7, 0.35, 0.55)
	panel_style.set_border_width_all(1)
	panel_style.set_corner_radius_all(8)
	panel_style.shadow_color = Color(0, 0, 0, 0.5)
	panel_style.shadow_size = 18
	panel_style.set_content_margin_all(24)
	$Dialog/Center/Panel.add_theme_stylebox_override("panel", panel_style)

	# Small and quiet, so it fits the gap below the creation screen's panel and does not compete with the HUD.
	var button_style := panel_style.duplicate() as StyleBoxFlat
	button_style.bg_color = Color(0.09, 0.09, 0.1, 0.7)
	button_style.shadow_size = 0
	button_style.set_corner_radius_all(6)
	button_style.content_margin_left = 8
	button_style.content_margin_right = 10
	button_style.content_margin_top = 3
	button_style.content_margin_bottom = 3
	var hover_style := button_style.duplicate() as StyleBoxFlat
	hover_style.bg_color = Color(0.16, 0.15, 0.13, 0.9)
	_button.add_theme_stylebox_override("normal", button_style)
	_button.add_theme_stylebox_override("hover", hover_style)
	_button.add_theme_stylebox_override("pressed", hover_style)

	var toast_style := panel_style.duplicate() as StyleBoxFlat
	toast_style.set_content_margin_all(12)
	toast_style.shadow_size = 8
	_toast.add_theme_stylebox_override("normal", toast_style)
