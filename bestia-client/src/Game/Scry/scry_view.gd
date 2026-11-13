class_name ScryView
extends Node3D

## Where a Sage's Scry looks. A scene-wide service in Game.tscn, found by group: the hotbar's Scry, the map and the
## mouse ask it. While a sight is open the camera rig leaves the body for this node, and terrain collision and grass
## follow it here.

signal sight_changed(is_open: bool)

## The way to the far spot opens from the map: the UI connects this to opening it.
signal map_requested

const SKILL_ID := 63
const _GROUP := "scry_view"

## Server argument for a party member as the target, see the server's `Scry` script.
const _PARTY_MEMBER := 1

var _is_open := false
var _rig: Node3D = null
var _scry_level := 0
## Master id to name, for every party member but us; empty outside a party.
var _party_members: Dictionary[int, String] = {}

var _return_button: Button


static func get_instance() -> ScryView:
	return (Engine.get_main_loop() as SceneTree).get_first_node_in_group(_GROUP) as ScryView


func _enter_tree() -> void:
	add_to_group(_GROUP)


func _ready() -> void:
	ConnectionManager.scry_sight_received.connect(_on_sight)
	ConnectionManager.entity_received.connect(_on_entity_received)
	ConnectionManager.party_message_received.connect(_on_party_message)
	_build_return_button()


func is_open() -> bool:
	return _is_open


## The level of Scry the master knows; 0 when it knows none, and nothing here offers it.
func scry_level() -> int:
	return _scry_level


func party_members() -> Dictionary[int, String]:
	return _party_members


## Where the world is looked at from: the far spot while a sight is open, [param body] otherwise.
func focus_or(body: Vector3) -> Vector3:
	return global_position if _is_open else body


## [param at] is a Godot position; the server finds the ground under it.
func scry_at(at: Vector3) -> void:
	ConnectionManager.activate_skill(SKILL_ID, _scry_level, at, 0, PackedInt64Array([0]))


func scry_member(master_id: int) -> void:
	ConnectionManager.activate_skill(SKILL_ID, _scry_level, Vector3.ZERO, 0, PackedInt64Array([_PARTY_MEMBER, master_id]))


## Casting Scry while it is open ends it, on the server's toggle path, which no cooldown holds up.
func return_to_body() -> void:
	if _is_open:
		ConnectionManager.activate_skill(SKILL_ID, maxi(_scry_level, 1))


func _unhandled_input(event: InputEvent) -> void:
	if _is_open and event.is_action_pressed("ui_cancel"):
		return_to_body()
		get_viewport().set_input_as_handled()


func _on_sight(message: ScrySightSMSG) -> void:
	if message.Open:
		global_position = message.Centre
		_is_open = true
		_take_camera()
	else:
		_is_open = false
		_give_camera_back()

	_return_button.visible = _is_open
	sight_changed.emit(_is_open)


func _take_camera() -> void:
	var body := EntityManager.get_instance().get_controlled_entity()
	if body == null or _rig != null:
		return
	_rig = body.get_camera_rig()
	if _rig != null:
		_rig.reparent(self, false)
		_rig.position = Vector3.ZERO


func _give_camera_back() -> void:
	if _rig == null or not is_instance_valid(_rig):
		_rig = null
		return

	var body := EntityManager.get_instance().get_controlled_entity()
	if body != null and body.get_camera_rig() == _rig:
		_rig.reparent(body, false)
		_rig.position = Vector3.ZERO
	else:
		# The body this rig belonged to is not driven any more; the one driven now has its own.
		_rig.queue_free()
	_rig = null


func _on_entity_received(message: EntitySMSG) -> void:
	if not message is SkillListSMSG:
		return
	var master := EntityManager.get_instance().get_master_entity()
	if master == null or message.EntityId != master.entity_id:
		return

	_scry_level = 0
	for entry in message.Skills:
		if entry.SkillId == SKILL_ID:
			_scry_level = maxi(_scry_level, entry.Level)


func _on_party_message(message: PartySMSG) -> void:
	if not message is PartyInfoSMSG:
		return

	var master_info = ConnectionManager.selected_master_info
	var own_master_id: int = int(master_info.MasterId) if master_info != null else 0
	_party_members.clear()
	for member in message.Members:
		if member.MasterId != own_master_id and member.IsOnline:
			_party_members[member.MasterId] = member.MasterName


func _build_return_button() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)

	_return_button = Button.new()
	_return_button.text = tr("SCRY_RETURN")
	_return_button.visible = false
	_return_button.set_anchors_and_offsets_preset(Control.PRESET_CENTER_TOP, Control.PRESET_MODE_MINSIZE, 24)
	_return_button.pressed.connect(return_to_body)
	layer.add_child(_return_button)
