class_name MonsterSensePopup
extends PanelContainer

## What Sense read off a monster, shown beside it until clicked away or for [constant _SHOW_SECONDS]. Follows the
## monster on screen while it lives; stays where it was last seen once it is gone.

const _SHOW_SECONDS := 20.0
const _OFFSET := Vector2(40, -60)

var _entity_id: int = 0
var _hide_at_msec: int = 0
var _title: Label
var _body: Label


func _ready() -> void:
	visible = false
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 6, 8, UiStyle.ACCENT, 1))
	var column := UiStyle.vbox(4)
	add_child(column)
	_title = UiStyle.label("", 14, UiStyle.ACCENT)
	column.add_child(_title)
	_body = UiStyle.label("", 12)
	column.add_child(_body)
	gui_input.connect(_on_gui_input)
	ConnectionManager.monster_sensed.connect(_on_sensed)
	set_process(false)


func _on_sensed(msg: MonsterSenseSMSG) -> void:
	_entity_id = msg.EntityId
	var name := _name_of(msg.EntityId)
	_title.text = tr("MONSTER_SENSE_TITLE") % [msg.Level, name]
	_body.text = tr("MONSTER_SENSE_BODY") % [
		msg.Element.capitalize(), msg.Kind.capitalize(), msg.Size.capitalize(),
		msg.Hp, msg.MaxHp, msg.Mana, msg.MaxMana,
		msg.Strength, msg.Vitality, msg.Intelligence,
		msg.Agility, msg.Willpower, msg.Dexterity,
		msg.Defense, msg.MagicDefense,
	]
	_hide_at_msec = Time.get_ticks_msec() + int(_SHOW_SECONDS * 1000.0)
	visible = true
	reset_size()
	set_process(true)
	_follow()


func _process(_delta: float) -> void:
	if Time.get_ticks_msec() >= _hide_at_msec:
		_close()
		return
	_follow()


func _follow() -> void:
	var entity := EntityManager.get_instance().get_entity(_entity_id)
	var camera := get_viewport().get_camera_3d()
	if entity == null or camera == null or camera.is_position_behind(entity.global_position):
		return
	var at := camera.unproject_position(entity.global_position) + _OFFSET
	var screen := get_viewport_rect().size
	position = Vector2(clampf(at.x, 0.0, screen.x - size.x), clampf(at.y, 0.0, screen.y - size.y))


func _on_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed:
		_close()


func _close() -> void:
	visible = false
	set_process(false)


func _name_of(entity_id: int) -> String:
	var entity := EntityManager.get_instance().get_entity(entity_id)
	return entity.get_display_name() if entity != null else "?"
