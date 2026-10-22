extends PanelContainer
class_name ShortcutContainer

signal shortcut_changed(row: int, number: int, shortcut_data: ShortcutData)
signal item_count_requested( row: int, number: int, item_id: int)

@export
var shortcut_row: int = 0

@export
var shortcut_number: int = 0

@onready var shortcut: Label = %Shortcut
@onready var _icon: TextureRect = %Icon
@onready var _count: Label = %Count
@onready var _count_bg: PanelContainer = %ShortcutBg
@onready var _shared_mark: Label = %SharedMark

const DISABLED_MODULATE: Color = Color(0.4, 0.4, 0.4, 0.6)
const SHARED_MARK := "★"

var _prompt_action: String = ""
var _shortcut_data: ShortcutData = ShortcutData.new()
var _is_dragging_self: bool = false
var _disabled: bool = false
## Shows the shared layer's slot because this layer's own slot is empty. Such a slot is edited on the shared layer
## only, so a drag here cannot move it away.
var _from_shared: bool = false
## The level the controlled entity can cast the slot's skill at. 0 when it does not know the skill.
var _usable_skill_level: int = 1
## Seconds left on a skill that is recovering, over the icon.
var _cooldown: Label


func _ready() -> void:
	# assigning your input action from Project Settings Input Map
	_prompt_action = "shortcut_%s_%s" % [shortcut_row, shortcut_number]
	if InputMap.has_action(_prompt_action):
		# Bindings are physical (US positions), so the label is translated to what the player's own layout prints
		# on that key: the key right of T reads Z on a German keyboard, not Y.
		var key_action = InputMap.action_get_events(_prompt_action)[0]
		var layout_keycode = DisplayServer.keyboard_get_keycode_from_physical(key_action.physical_keycode)
		shortcut.text = OS.get_keycode_string(layout_keycode)

	_cooldown = Label.new()
	_cooldown.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_cooldown.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_cooldown.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_cooldown.add_theme_font_size_override("font_size", 18)
	_cooldown.add_theme_constant_override("outline_size", 4)
	_cooldown.add_theme_color_override("font_outline_color", Color.BLACK)
	_cooldown.visible = false
	add_child(_cooldown)

	_update_display()


func _process(_delta: float) -> void:
	var left := 0.0
	if _shortcut_data.type == ShortcutData.ShortcutType.SKILL:
		left = SkillCooldowns.remaining_seconds(_shortcut_data.reference_id)

	_cooldown.visible = left > 0.0
	if _cooldown.visible:
		_cooldown.text = "%d" % ceili(left)
	_icon.modulate = DISABLED_MODULATE if _disabled or left > 0.0 else Color.WHITE


func _shortcut_input(event: InputEvent) -> void:
	# Exact, so a slot's bare key does not also fire under a modifier: Alt+G opens the guild window, it does not
	# use what sits on G.
	if event.is_action_pressed(_prompt_action, false, true):
		trigger_shortcut()
		get_viewport().set_input_as_handled()


func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var source_container = data.get("source_container", null)

	# Dragged from another shortcut slot: move it here, swapping if this slot
	# is already occupied, instead of leaving a copy behind.
	if source_container is ShortcutContainer and source_container != self:
		var previous_data: ShortcutData = _shortcut_data.duplicate()
		var previous_was_shared := _from_shared
		set_shortcut(data)

		if previous_data.is_empty() or previous_was_shared:
			source_container.clear_shortcut()
		else:
			source_container.set_shortcut(_to_shortcut_dict(previous_data))
	else:
		set_shortcut(data)


# Accepts items as well as skills, except for passive ones - those are always on and have no
# activation to bind a key to. Checked here and not only at the drag source (SkillRow) because a
# shortcut assigned before a skill became passive can still be dragged from slot to slot.
func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	if typeof(data) != TYPE_DICTIONARY:
		return false

	if data["type"] == "skill":
		return not _is_passive_skill(data["id"])

	return data["type"] == "item"


# A skill missing from the Attack DB is not treated as passive: it already reports itself loudly in
# _update_display(), and refusing the drop on top of that would just hide the actual problem.
func _is_passive_skill(skill_id: int) -> bool:
	var attack: AttackResource = AttackDB.get_instance().get_attack(skill_id)
	return attack != null and attack.is_passive


# Allows an assigned shortcut to be picked up and dragged elsewhere.
func _get_drag_data(_at_position: Vector2) -> Variant:
	if _shortcut_data.is_empty() or _from_shared:
		return null

	var preview: TextureRect = TextureRect.new()
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.size = Vector2(50, 50)
	preview.pivot_offset = preview.size / 2.0
	preview.texture = _icon.texture
	set_drag_preview(preview)

	_is_dragging_self = true

	var drag_data := _to_shortcut_dict(_shortcut_data)
	if drag_data.is_empty():
		return null

	drag_data["source_container"] = self
	return drag_data


# Converts shortcut data into the dictionary format used both for drag data
# and for set_shortcut(), so a slot's contents can be handed off to another.
func _to_shortcut_dict(data: ShortcutData) -> Dictionary:
	match data.type:
		ShortcutData.ShortcutType.ITEM:
			return {"type": "item", "id": data.reference_id}
		ShortcutData.ShortcutType.SKILL:
			return {"type": "skill", "id": data.reference_id, "level": data.skill_level}

	return {}


# NOTIFICATION_DRAG_END is broadcast to every control in the viewport, so only
# react when this container was the one that started the drag. If it wasn't
# dropped onto a valid target (e.g. dropped on the game background), remove it.
func _notification(what: int) -> void:
	if what == NOTIFICATION_DRAG_END and _is_dragging_self:
		_is_dragging_self = false
		if not get_viewport().gui_is_drag_successful():
			clear_shortcut()


func trigger_shortcut() -> void:
	if _shortcut_data.is_empty():
		return

	# Precondition no longer met (e.g. item left the inventory while offline).
	# Keep the assignment so it lights back up if the player gets the item
	# again, but don't let it fire.
	if _disabled:
		return

	# Default handling based on type
	match _shortcut_data.type:
		ShortcutData.ShortcutType.ITEM:
			_use_item()
		ShortcutData.ShortcutType.SKILL:
			_use_skill()


# Sets the shortcut depending on the dropped class.
func set_shortcut(data: Dictionary) -> void:
	_shortcut_data.clear()

	if data["type"] == "item":
		_shortcut_data.type = ShortcutData.ShortcutType.ITEM
		_shortcut_data.reference_id = data["id"]
	elif data["type"] == "skill":
		_shortcut_data.type = ShortcutData.ShortcutType.SKILL
		_shortcut_data.reference_id = data["id"]
		_shortcut_data.skill_level = data.get("level", 1)
		_usable_skill_level = _shortcut_data.skill_level

	_update_display()
	shortcut_changed.emit(shortcut_row, shortcut_number, _shortcut_data)


func get_shortcut_data() -> ShortcutData:
	return _shortcut_data


func set_shortcut_data(data: ShortcutData, from_shared: bool = false) -> void:
	_shortcut_data = data
	_usable_skill_level = data.skill_level
	_from_shared = from_shared
	_shared_mark.visible = from_shared
	tooltip_text = ("Shared slot %s: your master and every bestia use it.\n"
		+ "Drop something here to replace it on this bar only.") % [SHARED_MARK] if from_shared else ""
	_update_display()


func update_item_count(count: int) -> void:
	if _shortcut_data.type == ShortcutData.ShortcutType.ITEM:
		_count.text = str(count)
		_count_bg.visible = count > 0
		set_disabled(count <= 0)


func set_usable_skill_level(level: int) -> void:
	if _shortcut_data.type == ShortcutData.ShortcutType.SKILL:
		_usable_skill_level = level
		set_disabled(level <= 0)


# Grays out the icon and blocks trigger_shortcut() while keeping the
# assignment, drag/drop, and clearing fully functional.
func set_disabled(disabled: bool) -> void:
	_disabled = disabled
	_icon.modulate = DISABLED_MODULATE if disabled else Color.WHITE


func clear_shortcut() -> void:
	_shortcut_data.clear()
	_update_display()
	shortcut_changed.emit(shortcut_row, shortcut_number, _shortcut_data)


func _update_display() -> void:
	if _shortcut_data.is_empty():
		_icon.texture = null
		_icon.visible = false
		_count_bg.visible = false
		set_disabled(false)
		return

	_icon.visible = true

	match _shortcut_data.type:
		ShortcutData.ShortcutType.ITEM:
			var item = ItemDB.get_instance().get_item(_shortcut_data.reference_id)
			if item:
				_icon.texture = item.get_icon()
				# Request initial count update, which also refreshes the disabled state
				item_count_requested.emit(shortcut_row, shortcut_number, _shortcut_data.reference_id)
				_count.visible = true
			else:
				printerr("Item ID %s not found in item_db, can not display it" % [_shortcut_data.reference_id])
		ShortcutData.ShortcutType.SKILL:
			var attack = AttackDB.get_instance().get_attack(_shortcut_data.reference_id)
			if attack:
				_icon.texture = attack.get_icon()
			else:
				printerr("Attack ID %s not found in AttackDB, can not display it" % [_shortcut_data.reference_id])
			_count.visible = false
			set_disabled(false)


func _use_item() -> void:
	var item_res = ItemDB.get_instance().get_item(_shortcut_data.reference_id)
	if item_res == null:
		printerr("ShortcutContainer: Can not use item, no item returned for ID: %s" % [_shortcut_data.reference_id])
		return
	item_res.use_item()


func _use_skill() -> void:
	var attack = AttackDB.get_instance().get_attack(_shortcut_data.reference_id)
	if attack == null:
		printerr("ShortcutContainer: Can not use skill, no attack returned for ID: %s" % [_shortcut_data.reference_id])
		return
	attack.use_skill(_usable_skill_level)
