extends VBoxContainer
class_name DiscoveryPanel
## The "Discover" part of the crafting window: the player drops material piles in, sees the mix as a radar chart,
## and tries it against the blueprints of the skill the window was opened with.
##
## The chart is worked out here from [CraftAxes], because the mix only depends on what the player put in. Whether
## it finds anything is the server's to say: the answer names how close the mix came, and which way it is off.
## [b]The materials are always spent[/b], which the Try button says before it is pressed.

const _RadarChart := preload("res://Game/UI/Crafting/RadarChart/radar_chart.gd")

const _OUTCOME_TEXT := {
	"learned": "You grasp the pattern - blueprint learned! Use the skill again to see it in the list.",
	"slipped": "A pattern resonates here but slips away... The same mix can work next time.",
	"beyond_skill": "Something faint lies beyond your skill. Raise the skill rather than change the mix.",
	"nothing": "These materials resonate with nothing.",
}

## Every refusal a discovery can get. The crafting window shows the sentence; this only frees the Try button.
const _REFUSALS := ["craft_missing_materials", "craft_not_possible"]

## Set by [Crafting] from the last offer: which skill's blueprints a mix is tried against.
var _skill_id: int = 0

## player_item_id -> {"item_id": int, "amount": int, "held": int}, in the order they were dropped in.
var _materials: Dictionary = {}

var _awaiting_result := false

var _rows: VBoxContainer
var _chart: Control
var _compare: OptionButton
var _try: Button
var _result: Label


func _ready() -> void:
	add_theme_constant_override("separation", 6)

	var title := Label.new()
	title.text = "Discover"
	add_child(title)

	var body := HBoxContainer.new()
	body.add_theme_constant_override("separation", 10)
	add_child(body)

	var drop_zone := _MaterialDropZone.new()
	drop_zone.panel = self
	drop_zone.custom_minimum_size = Vector2(220, 200)
	drop_zone.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	body.add_child(drop_zone)

	_rows = VBoxContainer.new()
	_rows.mouse_filter = Control.MOUSE_FILTER_PASS
	drop_zone.add_child(_rows)

	_chart = _RadarChart.new()
	_chart.custom_minimum_size = Vector2(240, 220)
	body.add_child(_chart)

	var controls := HBoxContainer.new()
	controls.add_theme_constant_override("separation", 8)
	add_child(controls)

	var compare_label := Label.new()
	compare_label.text = "Compare with:"
	controls.add_child(compare_label)

	_compare = OptionButton.new()
	_compare.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_compare.item_selected.connect(_on_compare_selected)
	controls.add_child(_compare)

	_try = Button.new()
	_try.text = "Try (uses the materials)"
	_try.pressed.connect(_on_try_pressed)
	controls.add_child(_try)

	_result = Label.new()
	_result.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_result.add_theme_font_size_override("font_size", 12)
	add_child(_result)

	ConnectionManager.discovery_result_received.connect(_on_discovery_result)
	ConnectionManager.operation_error.connect(_on_operation_error)

	_refresh()


## Called by [Crafting] with every offer, so the panel knows the skill and the known blueprints to compare with.
func set_offer(msg: CraftableRecipesSMSG) -> void:
	_skill_id = int(msg.SkillId)
	_result.text = ""
	_awaiting_result = false

	_compare.clear()
	_compare.add_item("Nothing")
	_compare.set_item_metadata(0, PackedFloat32Array())
	var item_db := ItemDB.get_instance()
	for recipe in msg.Recipes:
		if recipe.Point.size() != CraftAxes.AXES.size():
			continue
		var output = item_db.get_item(recipe.OutputItemId) if item_db else null
		_compare.add_item(tr(output.name_key) if output else "Recipe %d" % recipe.RecipeId)
		_compare.set_item_metadata(_compare.item_count - 1, PackedFloat32Array(recipe.Point))
	_compare.select(0)
	_chart.set_outline(PackedFloat32Array())

	_refresh()


## A pile dragged in from the inventory. Only materials have axes, so anything else is turned away.
func add_material(player_item_id: int, item_id: int, held: int) -> void:
	if CraftAxes.of(item_id).is_empty():
		_result.text = "That is not a crafting material."
		return

	if not _materials.has(player_item_id):
		_materials[player_item_id] = {"item_id": item_id, "amount": 1, "held": held}
	_refresh()


func _remove_material(player_item_id: int) -> void:
	_materials.erase(player_item_id)
	_refresh()


func _set_amount(player_item_id: int, amount: float) -> void:
	_materials[player_item_id]["amount"] = int(amount)
	_chart.set_mix(_mix())


func _refresh() -> void:
	for child in _rows.get_children():
		_rows.remove_child(child)
		child.queue_free()

	if _materials.is_empty():
		var hint := Label.new()
		hint.text = "Drag materials here from your bag."
		hint.add_theme_color_override("font_color", Color(1, 1, 1, 0.5))
		_rows.add_child(hint)

	var item_db := ItemDB.get_instance()
	for player_item_id in _materials:
		var entry: Dictionary = _materials[player_item_id]
		var row := HBoxContainer.new()
		row.mouse_filter = Control.MOUSE_FILTER_PASS

		var item = item_db.get_item(entry["item_id"]) if item_db else null
		var name_label := Label.new()
		name_label.text = tr(item.name_key) if item else str(entry["item_id"])
		name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(name_label)

		var amount := SpinBox.new()
		amount.min_value = 1
		amount.max_value = maxi(1, entry["held"])
		amount.value = entry["amount"]
		amount.value_changed.connect(_set_amount.bind(player_item_id))
		row.add_child(amount)

		var remove := Button.new()
		remove.text = "x"
		remove.pressed.connect(_remove_material.bind(player_item_id))
		row.add_child(remove)

		_rows.add_child(row)

	_chart.set_mix(_mix())
	_try.disabled = _awaiting_result or _materials.is_empty() or _skill_id == 0


## The amount-weighted mean of the materials' axes - the same mix the server works out.
func _mix() -> PackedFloat32Array:
	var total := 0
	var sums := PackedFloat32Array()
	sums.resize(CraftAxes.AXES.size())
	for entry in _materials.values():
		var axes := CraftAxes.of(entry["item_id"])
		for i in axes.size():
			sums[i] += axes[i] * entry["amount"]
		total += entry["amount"]

	if total == 0:
		return PackedFloat32Array()
	for i in sums.size():
		sums[i] /= total
	return sums


func _on_compare_selected(index: int) -> void:
	_chart.set_outline(_compare.get_item_metadata(index))


func _on_try_pressed() -> void:
	if not ConnectionManager.is_ready_to_send():
		return

	var amounts := {}
	for player_item_id in _materials:
		amounts[player_item_id] = _materials[player_item_id]["amount"]
	ConnectionManager.discover_recipe(_skill_id, amounts)

	_awaiting_result = true
	_result.text = "Trying..."
	_refresh()


func _on_discovery_result(msg: DiscoveryResultSMSG) -> void:
	var text: String = _OUTCOME_TEXT.get(msg.OutcomeName, "")
	var hints: Array = []
	for hint in msg.Hints:
		hints.append(CraftAxes.hint_text(hint.Axis, hint.TooHigh))
	if not hints.is_empty():
		text += " The mix is " + ", ".join(hints) + "."

	_result.text = text
	_awaiting_result = false
	# The piles shrank; what is left is the player's to pick again.
	_materials.clear()
	_refresh()


func _on_operation_error(message) -> void:
	if _awaiting_result and message.CodeName in _REFUSALS:
		_awaiting_result = false
		_result.text = ""
		_refresh()


## Takes inventory rows dropped on the material list and hands them to the panel.
class _MaterialDropZone:
	extends PanelContainer

	var panel


	func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
		return typeof(data) == TYPE_DICTIONARY and data.get("source") == "inventory_item"


	func _drop_data(_at_position: Vector2, data: Variant) -> void:
		panel.add_material(data.get("player_item_id", 0), data.get("id", 0), data.get("amount", 0))
