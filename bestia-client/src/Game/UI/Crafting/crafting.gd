extends PanelContainer
class_name Crafting
## Lists what the player can make at the station they are standing next to, and asks the server to make it.
##
## Filled entirely by the server: activating a crafting skill answers with a [CraftableRecipesSMSG] carrying
## every recipe, its success chance and its duration, all of which depend on invested skill levels and on which
## station is in range - so none of it can be computed here. The window opens when that message arrives rather
## than from a hotkey, because there is nothing to show until it does.
##
## [b]Nothing is applied locally.[/b] A craft may be refused on arrival and may still fail when it resolves; both
## come back as an [code]OperationError[/code], and the inventory updates itself through the ordinary component
## sync either way.

## Emitted when the server has offered recipes, so [code]ui.gd[/code] can bring the window forward.
signal recipes_offered()

const CraftingRowScene = preload("res://Game/UI/Crafting/CraftingRow/CraftingRow.tscn")

## What the title says when the work needs no station - a meal, a ritual, an upgrade.
const _NO_STATION_TITLE := "Handiwork"

## Every message the crafting flow can answer with, by [code]OperationError.CodeName[/code] /
## [code]OperationSuccess.CodeName[/code]. Matching on the name rather than the ordinal is what keeps a new
## denial reason from being re-declared here as a bare number.
const _RESULT_MESSAGES := {
	"craft_succeeded": "Done.",
	"craft_failed": "The attempt failed. The materials are gone.",
	"craft_item_destroyed": "The cut went wrong and the item was destroyed.",
	"craft_missing_materials": "Not enough materials.",
	"craft_already_in_progress": "Already working on something.",
	"craft_not_possible": "You cannot do that here.",
}

## Assigned at runtime by Game/UI/ui.gd, for the same reason the Inventory <-> Equipment pair is: the Inventory
## node only exists once its WidgetWindow has instantiated its content, so no editor-wired NodePath can reach it.
## Untyped on purpose to avoid a load cycle through the Inventory class.
var inventory = null

@onready var _station_label: Label = %StationLabel
@onready var _target_row: HBoxContainer = %TargetRow
@onready var _target_picker: OptionButton = %TargetPicker
@onready var _recipe_rows: VBoxContainer = %RecipeRows
@onready var _status: Label = %Status

## True between asking for a craft and hearing how it went, which is when a second request would be refused.
var _awaiting_result: bool = false


func _ready() -> void:
	ConnectionManager.craftable_recipes_received.connect(_on_craftable_recipes)
	ConnectionManager.operation_error.connect(_on_operation_result)
	ConnectionManager.operation_success.connect(_on_operation_result)
	_target_picker.item_selected.connect(_on_target_selected)

	_clear()


func _on_craftable_recipes(msg: CraftableRecipesSMSG) -> void:
	# The name is resolved on the C# side, which is where the StaticEntityKind ordinals are already mirrored.
	_station_label.text = msg.StationName if not msg.StationName.is_empty() else _NO_STATION_TITLE
	_status.text = ""
	_awaiting_result = false

	_clear()

	if msg.Recipes.is_empty():
		_status.text = "Nothing you can make here yet."

	var any_needs_target := false
	for recipe in msg.Recipes:
		var row = CraftingRowScene.instantiate()
		_recipe_rows.add_child(row)
		row.initialize(recipe)
		row.craft_requested.connect(_on_craft_requested)
		any_needs_target = any_needs_target or recipe.NeedsTarget

	# One picker for the whole window rather than one per row: a recipe that changes an item needs the player to
	# say which item, and every such recipe wants the same list.
	_target_row.visible = any_needs_target
	if any_needs_target:
		_fill_target_picker()

	_update_buttons()
	recipes_offered.emit()


## The held items a targeted recipe can act on: unique instances that are not being worn, because every
## item-changing effect refuses worn gear - you take it off to work on it.
func _fill_target_picker() -> void:
	_target_picker.clear()

	if inventory == null:
		return

	for held in inventory.held_instances():
		var label := tr(held.item.name_key)
		if held.upgrade_level > 0:
			label += " +%d" % held.upgrade_level
		if held.max_durability > 0:
			label += " (%d/%d)" % [held.durability, held.max_durability]

		_target_picker.add_item(label)
		_target_picker.set_item_metadata(_target_picker.item_count - 1, held.player_item_id)


func _selected_target() -> int:
	var index := _target_picker.selected
	if index < 0:
		return 0

	var unique_id = _target_picker.get_item_metadata(index)
	return int(unique_id) if unique_id != null else 0


func _on_craft_requested(recipe_id: int) -> void:
	if not ConnectionManager.is_ready_to_send():
		return

	ConnectionManager.craft_item(recipe_id, _selected_target())

	_awaiting_result = true
	_status.text = "Working..."
	_update_buttons()


## Both channels land here. A refusal and a failure read the same from this side - the craft did not produce
## anything and the window is free again - so they differ only in the sentence shown.
func _on_operation_result(message) -> void:
	var text: String = _RESULT_MESSAGES.get(message.CodeName, "")
	if text.is_empty():
		# Some other operation entirely (equipping, a master edit): not ours to report on.
		return

	_status.text = text
	_awaiting_result = false
	_update_buttons()

	# The list is stale the moment materials are spent, and only the server can price it again.
	_mark_rows_stale()


## Greys every Craft button while a craft is outstanding, and greys a targeted recipe when there is nothing
## picked for it to work on.
func _update_buttons() -> void:
	var has_target := _selected_target() != 0

	for row in _recipe_rows.get_children():
		row.set_can_craft(not _awaiting_result and (has_target or not row.needs_target()))


## Materials and odds are now wrong and cannot be recomputed here, so the row says so rather than showing a
## number that is no longer true. The next activation refreshes it.
func _mark_rows_stale() -> void:
	if _recipe_rows.get_child_count() > 0:
		_status.text += " Use the skill again to refresh."


func _on_target_selected(_index: int) -> void:
	_update_buttons()


func _clear() -> void:
	for child in _recipe_rows.get_children():
		_recipe_rows.remove_child(child)
		child.queue_free()
