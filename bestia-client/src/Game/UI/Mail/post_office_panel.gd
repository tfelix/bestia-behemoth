class_name PostOfficePanel
extends PanelContainer

## The post office: parcels and the home office. Letters need no office and live in [LetterPanel].
##
## Opened by the server when the player talks to a postmaster at work, and closed by walking away, like the
## shop. Built in code, like [AuctionHousePanel].

signal post_office_opened()
signal post_office_closed()

## How far the player may wander from where the window opened before it closes. The shop's distance: the
## postmaster stands behind a counter, not in a hall.
const _WALK_AWAY_METRES := 10.0

## Hours between coaches, by office level. Must match the server's timetable and the design docs.
const _COACH_HOURS := {1: 6, 2: 3, 3: 2}

## The postmaster the window is open at. Zero while closed. Every request names them.
var _postmaster_id: int = 0
var _opened_at := Vector3.ZERO

var _office: Label
var _coach: Label
var _home: Label
var _make_home: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(560, 380)
	_build()

	ConnectionManager.post_office_opened.connect(_on_opened)
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func is_open() -> bool:
	return _postmaster_id != 0


func close_office() -> void:
	if not is_open():
		return

	_postmaster_id = 0
	set_process(false)
	post_office_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_office()


func _on_opened(msg: PostOfficeOpenSMSG) -> void:
	var was_open := is_open()
	_postmaster_id = msg.PostmasterEntityId

	var player := _player()
	if not was_open:
		_opened_at = player.global_position if player != null else Vector3.ZERO
	set_process(player != null)

	_office.text = tr("POST_OFFICE_TITLE") % msg.OfficeName
	_coach.text = tr("POST_OFFICE_COACH") % _COACH_HOURS.get(msg.Level, 3)
	_home.text = tr("POST_OFFICE_HOME") % msg.HomeOfficeName if not msg.HomeOfficeName.is_empty() else tr("POST_OFFICE_NO_HOME")
	_make_home.disabled = msg.IsHome
	_make_home.text = tr("POST_OFFICE_IS_HOME") if msg.IsHome else tr("POST_OFFICE_MAKE_HOME")

	if not was_open:
		post_office_opened.emit()


## The window's own close button only hides it.
func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_office()


func _on_make_home_pressed() -> void:
	if is_open():
		_make_home.disabled = true
		ConnectionManager.set_home_post_office(_postmaster_id)


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_office = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_office)
	_coach = UiStyle.label("", 11, UiStyle.MUTED)
	body.add_child(_coach)

	var home_row := UiStyle.hbox(8)
	_home = UiStyle.label("", 13)
	home_row.add_child(_home)
	home_row.add_child(UiStyle.spacer())
	_make_home = Button.new()
	_make_home.pressed.connect(_on_make_home_pressed)
	home_row.add_child(_make_home)
	body.add_child(home_row)

	var parcels := CenterContainer.new()
	parcels.size_flags_vertical = Control.SIZE_EXPAND_FILL
	parcels.add_child(UiStyle.label(tr("POST_OFFICE_NO_PARCELS"), 13, UiStyle.MUTED))
	body.add_child(parcels)
