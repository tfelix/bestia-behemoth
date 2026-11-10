extends PanelContainer
class_name Crafting
## The one crafting window: what the player can make at the station they stand at, or by hand.
##
## Read from left to right: a rail of the skills that work here, the recipes of the picked skill, and the detail
## of the picked recipe with how many to make. Discovery sits behind a button of its own, because it is used far
## less often than making something known.
##
## Filled entirely by the server: a [CraftableRecipesSMSG] carries every recipe, its success chance and its
## duration, all of which depend on invested skill levels and on which station is in range - so none of it can be
## computed here. The window opens when that message arrives.
##
## [b]Nothing is applied locally.[/b] A craft may be refused on arrival and may still fail when it resolves; both
## come back as an [code]OperationError[/code], and the inventory updates itself through the ordinary component
## sync either way.

## Emitted when the server has offered recipes, so [code]ui.gd[/code] can bring the window forward.
signal recipes_offered()

const DiscoveryPanelScript = preload("res://Game/UI/Crafting/Discovery/discovery_panel.gd")

## What the title says when the work needs no station.
const _NO_STATION_TITLE := "Handiwork"

## M_BASIC_SKILL: every master has it, so its recipes are the basics rather than a skill of their own.
const _BASIC_SKILL_ID := 1
const _BASIC_SKILL_TITLE := "Basics"

## What the three item-changing effects are called, keyed by [member CraftableRecipe.EffectName].
## Hardcoded English for now, like the rest of this window - none of it has a localization row yet.
const _EFFECT_TITLES := {
	"add_slot": "Cut a rune slot",
	"upgrade": "Upgrade equipment",
	"repair": "Repair equipment",
}

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
	"craft_item_too_advanced": "That is beyond you for now. Raise the skill to reach a higher tier of item.",
}

const _SHORT_COLOR := UiStyle.DANGER

const _RAIL_ICON_SIZE := 24

## Why a station's queue stands still, keyed by [member StationStateSMSG.PauseName].
const _PAUSE_TEXTS := {
	"worker_away": "Paused: the worker is too far away.",
	"worker_offline": "Paused: the worker is not here.",
	"bag_full": "Paused: the worker's bag is full.",
	"station_out": "Paused: the fire is out.",
	"cannot_craft": "Paused: the worker cannot make this.",
}

## Assigned at runtime by Game/UI/ui.gd, for the same reason the Inventory <-> Equipment pair is: the Inventory
## node only exists once its WidgetWindow has instantiated its content, so no editor-wired NodePath can reach it.
## Untyped on purpose to avoid a load cycle through the Inventory class.
var inventory = null

@onready var _station_label: Label = %StationLabel
@onready var _fuel_label: Label = %FuelLabel
@onready var _add_wood_button: Button = %AddWoodButton
@onready var _make_page: Control = %MakePage
@onready var _skill_rail: VBoxContainer = %SkillRail
@onready var _skill_title: Label = %SkillTitle
@onready var _recipe_rows: VBoxContainer = %RecipeRows
@onready var _discover_button: Button = %DiscoverButton
@onready var _detail: Control = %Detail
@onready var _recipe_name: Label = %RecipeName
@onready var _inputs: VBoxContainer = %Inputs
@onready var _odds: Label = %Odds
@onready var _target_row: HBoxContainer = %TargetRow
@onready var _target_picker: OptionButton = %TargetPicker
@onready var _actions: HBoxContainer = %Actions
@onready var _discover_page: Control = %DiscoverPage
@onready var _queue_panel: Control = %QueuePanel
@onready var _queue_rows: VBoxContainer = %QueueRows
@onready var _pause_label: Label = %PauseLabel
@onready var _held_label: Label = %HeldLabel
@onready var _worker_picker: OptionButton = %WorkerPicker
@onready var _status: Label = %Status

## Skill id -> its recipes, in the order the server listed them.
var _recipes_by_skill: Dictionary = {}
var _skill_order: Array[int] = []

## The player structure the window is open at, or 0 for Handiwork.
var _structure_id: int = 0

var _selected_skill: int = 0
var _selected_recipe: CraftableRecipe = null
var _amount: int = 1

## Whether the rail shows names or only icons. It folds once a skill is picked.
var _rail_folded := false

## True between asking for a craft and hearing how it went, which is when a second request would be refused.
var _awaiting_result: bool = false

## True between changing a station's queue and its answer, after which the bag counts are stale.
var _awaiting_queue: bool = false

## The bar of the item in progress, which runs on between two station states.
var _progress_bar: ProgressBar = null
var _progress_millis: float = 0.0
var _craft_millis: float = 0.0
var _progress_running := false

var _discovery: DiscoveryPanel


func _ready() -> void:
	ConnectionManager.craftable_recipes_received.connect(_on_craftable_recipes)
	ConnectionManager.operation_error.connect(_on_operation_result)
	ConnectionManager.operation_success.connect(_on_operation_result)
	ConnectionManager.discovery_result_received.connect(_on_discovery_result)
	ConnectionManager.station_state_received.connect(_on_station_state)
	_target_picker.item_selected.connect(func(_index: int) -> void: _show_actions())
	_discover_button.pressed.connect(_show_discovery)
	%BackButton.pressed.connect(_show_recipes)
	_add_wood_button.pressed.connect(_on_add_wood_pressed)
	_worker_picker.item_selected.connect(_on_worker_selected)

	_emphasize(_discover_button)

	_discovery = DiscoveryPanelScript.new()
	_discovery.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_discover_page.add_child(_discovery)

	_show_recipes()


func _on_craftable_recipes(msg: CraftableRecipesSMSG) -> void:
	# The name is resolved on the C# side, which is where the StaticEntityKind ordinals are already mirrored.
	_station_label.text = msg.StationName if not msg.StationName.is_empty() else _NO_STATION_TITLE
	if int(msg.StructureId) != _structure_id:
		_clear(_queue_rows)
	_structure_id = int(msg.StructureId)
	_queue_panel.visible = _at_station()
	_show_fuel(msg)
	_awaiting_result = false

	var kept_recipe_id := int(_selected_recipe.RecipeId) if _selected_recipe != null else 0
	_group_by_skill(msg.Recipes)

	if _recipes_by_skill.has(int(msg.SkillId)):
		_selected_skill = int(msg.SkillId)
	elif not _recipes_by_skill.has(_selected_skill):
		_selected_skill = _skill_order[0] if _skill_order.size() == 1 else 0
	_rail_folded = _selected_skill != 0

	_selected_recipe = _recipe_of(kept_recipe_id)
	if _selected_recipe == null:
		_amount = 1

	if _skill_order.is_empty():
		_status.text = "Nothing you can make here yet."

	_rebuild()
	recipes_offered.emit()


func _group_by_skill(recipes: Array) -> void:
	_recipes_by_skill.clear()
	_skill_order.clear()

	for recipe in recipes:
		var skill_id := int(recipe.SkillId)
		if not _recipes_by_skill.has(skill_id):
			_recipes_by_skill[skill_id] = []
			_skill_order.append(skill_id)
		_recipes_by_skill[skill_id].append(recipe)


func _recipe_of(recipe_id: int) -> CraftableRecipe:
	for recipe in _recipes_by_skill.get(_selected_skill, []):
		if int(recipe.RecipeId) == recipe_id:
			return recipe
	return null


func _rebuild() -> void:
	_build_rail()
	_build_recipe_list()
	_build_detail()
	_discovery.set_offer(_selected_skill, _recipes_by_skill.get(_selected_skill, []))


## A campfire says how long it burns and takes wood. The time is the server's at the moment it answered.
func _show_fuel(msg: CraftableRecipesSMSG) -> void:
	_fuel_label.visible = msg.BurnsFuel
	_add_wood_button.visible = msg.BurnsFuel
	if not msg.BurnsFuel:
		return

	var minutes := ceili(msg.FuelSeconds / 60.0)
	_fuel_label.text = "Burns %d min more" % minutes if msg.FuelSeconds > 0 else "The fire is out"


func _on_add_wood_pressed() -> void:
	if _structure_id != 0 and ConnectionManager.is_ready_to_send():
		ConnectionManager.add_wood(_structure_id)


# --- Skill rail ---------------------------------------------------------------------------------------------------

## One button per skill. A single skill needs no rail: there is nothing to pick between.
func _build_rail() -> void:
	_clear(_skill_rail)
	_skill_rail.visible = _skill_order.size() > 1

	for skill_id in _skill_order:
		var button := Button.new()
		button.toggle_mode = true
		button.button_pressed = skill_id == _selected_skill
		button.focus_mode = Control.FOCUS_NONE
		button.alignment = HORIZONTAL_ALIGNMENT_LEFT
		button.icon = _icon_of(skill_id)
		button.add_theme_constant_override("icon_max_width", _RAIL_ICON_SIZE)
		button.custom_minimum_size = Vector2(40, 40) if _rail_folded else Vector2(170, 40)
		button.text = "" if _rail_folded else _title_of_skill(skill_id)
		button.tooltip_text = _title_of_skill(skill_id)
		button.pressed.connect(_on_skill_pressed.bind(skill_id))
		_skill_rail.add_child(button)


## Picking a skill folds the rail; picking it again unfolds it, so the names come back when wanted.
func _on_skill_pressed(skill_id: int) -> void:
	if skill_id == _selected_skill:
		_rail_folded = not _rail_folded
	else:
		_selected_skill = skill_id
		_selected_recipe = null
		_amount = 1
		_rail_folded = true

	_rebuild()


func _title_of_skill(skill_id: int) -> String:
	if skill_id == _BASIC_SKILL_ID:
		return _BASIC_SKILL_TITLE

	var attack := AttackDB.get_instance().get_attack(skill_id)
	return tr(attack.name) if attack else "Skill %d" % skill_id


func _icon_of(skill_id: int) -> Texture2D:
	var attack := AttackDB.get_instance().get_attack(skill_id)
	return attack.get_icon() if attack else null


# --- Recipe list --------------------------------------------------------------------------------------------------

func _build_recipe_list() -> void:
	_clear(_recipe_rows)
	_skill_title.text = _title_of_skill(_selected_skill) if _selected_skill != 0 else "Pick a skill"
	_discover_button.visible = _selected_skill != 0 and _selected_skill != _BASIC_SKILL_ID

	for recipe in _recipes_by_skill.get(_selected_skill, []):
		_recipe_rows.add_child(_recipe_row(recipe))


## "Torch   [3]": the number is how many the player can make from what they carry.
func _recipe_row(recipe: CraftableRecipe) -> Button:
	var row := Button.new()
	row.toggle_mode = true
	row.button_pressed = recipe == _selected_recipe
	row.focus_mode = Control.FOCUS_NONE
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.flat = recipe != _selected_recipe

	var craftable := _craftable_count(recipe)
	row.text = _title_of(recipe) + ("   [%d]" % craftable if craftable > 0 and not recipe.NeedsTarget else "")
	if craftable == 0:
		row.add_theme_color_override("font_color", UiStyle.MUTED)

	row.pressed.connect(_on_recipe_pressed.bind(recipe))
	return row


func _on_recipe_pressed(recipe: CraftableRecipe) -> void:
	_selected_recipe = recipe
	_amount = 1
	_build_recipe_list()
	_build_detail()


func _craftable_count(recipe: CraftableRecipe) -> int:
	if recipe.Inputs.is_empty():
		return 0

	var count := 1 << 30
	for input in recipe.Inputs:
		count = mini(count, floori(float(input.Held) / maxi(1, int(input.Amount))))
	return count


# --- Detail -------------------------------------------------------------------------------------------------------

func _build_detail() -> void:
	_clear(_inputs)
	_detail.visible = _selected_recipe != null
	if _selected_recipe == null:
		return

	var recipe := _selected_recipe
	_recipe_name.text = _title_of(recipe)

	for input in recipe.Inputs:
		var need := int(input.Amount) * _amount
		var line := UiStyle.hbox(8)
		line.add_child(UiStyle.label(_item_name(int(input.ItemId)), 13))
		line.add_child(UiStyle.spacer())
		line.add_child(UiStyle.label("%d / %d" % [input.Held, need], 13, UiStyle.TEXT if input.Held >= need else _SHORT_COLOR))
		_inputs.add_child(line)

	_odds.text = "%.1f s · %d %% each" % [recipe.CraftMillis / 1000.0, roundi(recipe.SuccessPermille / 10.0)]

	# One picker for every targeted recipe: they all want the same list of held items.
	_target_row.visible = recipe.NeedsTarget
	if recipe.NeedsTarget:
		_fill_target_picker()

	_show_actions()


## The stepper, Max and the craft button. A recipe that works on an item makes one at a time.
func _show_actions() -> void:
	_clear(_actions)
	if _selected_recipe == null:
		return

	var recipe := _selected_recipe
	var most := maxi(1, _craftable_count(recipe))

	if not recipe.NeedsTarget:
		var less := UiStyle.glyph_button("−", "Fewer")
		less.disabled = _amount <= 1
		less.pressed.connect(func() -> void: _set_amount(_amount - _step()))
		_actions.add_child(less)

		var count := UiStyle.label(str(_amount), 13)
		count.custom_minimum_size = Vector2(30, 0)
		count.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		_actions.add_child(count)

		var more := UiStyle.glyph_button("+", "More")
		more.disabled = _amount >= most
		more.pressed.connect(func() -> void: _set_amount(_amount + _step()))
		_actions.add_child(more)

		var max_button := Button.new()
		max_button.text = "Max %d" % most
		max_button.focus_mode = Control.FOCUS_NONE
		max_button.pressed.connect(func() -> void: _set_amount(most))
		_actions.add_child(max_button)

	_actions.add_child(UiStyle.spacer())

	var make := Button.new()
	make.text = _make_label(recipe)
	make.focus_mode = Control.FOCUS_NONE
	make.disabled = _awaiting_result or (recipe.NeedsTarget and _selected_target() == 0)
	make.pressed.connect(_on_make_pressed)
	_emphasize(make)
	_actions.add_child(make)


## At a station a recipe that makes something is queued there; everything else is made at once.
func _queues(recipe: CraftableRecipe) -> bool:
	return _at_station() and not recipe.NeedsTarget


func _make_label(recipe: CraftableRecipe) -> String:
	var verb := "Queue" if _queues(recipe) else "Make"
	return "%s %d" % [verb, _amount] if _amount > 1 else verb


func _at_station() -> bool:
	return _structure_id != 0


func _set_amount(amount: int) -> void:
	_amount = clampi(amount, 1, maxi(1, _craftable_count(_selected_recipe)))
	_build_detail()


## Shift steps by ten, as in the shop.
func _step() -> int:
	return 10 if Input.is_key_pressed(KEY_SHIFT) else 1


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


func _on_make_pressed() -> void:
	if _selected_recipe == null or not ConnectionManager.is_ready_to_send():
		return

	if _queues(_selected_recipe):
		ConnectionManager.queue_craft(_structure_id, int(_selected_recipe.RecipeId), _amount)
		_awaiting_queue = true
		return

	ConnectionManager.craft_item(int(_selected_recipe.RecipeId), _selected_target(), _amount)

	_awaiting_result = true
	_status.text = "Working..."
	_show_actions()


# --- Results ------------------------------------------------------------------------------------------------------

## Both channels land here. A refusal and a failure read the same from this side - the craft did not produce
## anything - so they differ only in the sentence shown.
func _on_operation_result(message) -> void:
	var text: String = _RESULT_MESSAGES.get(message.CodeName, "")
	if text.is_empty():
		# Some other operation entirely (equipping, a master edit): not ours to report on.
		return

	_status.text = text
	_awaiting_result = false
	_refresh_offer()


## A learned blueprint belongs in the list now.
func _on_discovery_result(msg: DiscoveryResultSMSG) -> void:
	if msg.OutcomeName == "learned":
		_refresh_offer()


## Materials and odds are wrong the moment a craft resolves, and only the server can price them again.
func _refresh_offer() -> void:
	if is_visible_in_tree() and ConnectionManager.is_ready_to_send():
		ConnectionManager.open_crafting(_selected_skill, _structure_id)


# --- Station queue ------------------------------------------------------------------------------------------------

func _on_station_state(msg: StationStateSMSG) -> void:
	if int(msg.StructureId) != _structure_id:
		return

	_clear(_queue_rows)
	_progress_bar = null
	for index in msg.Entries.size():
		_queue_rows.add_child(_queue_row(msg.Entries[index], index))

	_fill_worker_picker(int(msg.WorkerEntityId))
	_pause_label.text = _PAUSE_TEXTS.get(msg.PauseName, "")
	_progress_running = msg.PauseName == "none"
	_held_label.text = _held_text(msg.Held)

	if msg.Entries.is_empty():
		_queue_rows.add_child(UiStyle.label("Nothing queued.", 12, UiStyle.MUTED))

	# The ingredients just moved between the bag and the station, so the counts in the list are wrong now.
	if _awaiting_queue:
		_awaiting_queue = false
		_refresh_offer()


## "Chair   2 of 5   [=====-----]   x"
func _queue_row(entry: StationQueueEntry, index: int) -> HBoxContainer:
	var row := UiStyle.hbox(8)
	var recipe := _recipe_by_id(int(entry.RecipeId))
	var name_label := UiStyle.label(_title_of(recipe) if recipe else "?", 13)
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(name_label)
	row.add_child(UiStyle.label("%d of %d" % [entry.Done, entry.Amount], 12, UiStyle.MUTED))

	if index == 0:
		_progress_bar = UiStyle.bar(UiStyle.ACCENT, 6)
		_progress_bar.custom_minimum_size = Vector2(120, 6)
		_progress_bar.size_flags_horizontal = Control.SIZE_SHRINK_END
		_progress_bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		_progress_millis = entry.ProgressMillis
		_craft_millis = entry.CraftMillis
		_update_progress_bar()
		row.add_child(_progress_bar)

	var cancel := UiStyle.glyph_button("×", "Take off the queue")
	cancel.pressed.connect(_on_cancel_pressed.bind(index))
	row.add_child(cancel)
	return row


func _process(delta: float) -> void:
	if _progress_running and _progress_bar != null:
		_progress_millis = minf(_progress_millis + delta * 1000.0, _craft_millis)
		_update_progress_bar()


func _update_progress_bar() -> void:
	if _progress_bar != null and _craft_millis > 0:
		UiStyle.set_bar(_progress_bar, int(_progress_millis), int(_craft_millis))


func _on_cancel_pressed(index: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.cancel_queue_entry(_structure_id, index)
		_awaiting_queue = true


## The master and each of their bestias. A bestia keeps working while the master is logged out, so the picker is
## how a player leaves a station running.
func _fill_worker_picker(worker_entity_id: int) -> void:
	_worker_picker.clear()
	var master_name := ConnectionManager.selected_master_info.Name if ConnectionManager.selected_master_info else "You"
	_worker_picker.add_item(master_name)
	_worker_picker.set_item_metadata(0, 0)

	var entities := EntityManager.get_instance()
	for bestia in entities.get_owned_bestias() if entities else []:
		_worker_picker.add_item(OwnedSlots.name_of(bestia))
		var index := _worker_picker.item_count - 1
		_worker_picker.set_item_metadata(index, int(bestia.EntityId))
		if int(bestia.EntityId) == worker_entity_id:
			_worker_picker.select(index)

	if worker_entity_id == 0:
		_worker_picker.select(0)


func _on_worker_selected(index: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.set_station_worker(_structure_id, int(_worker_picker.get_item_metadata(index)))


func _held_text(held: Dictionary) -> String:
	if held.is_empty():
		return ""

	var piles: Array[String] = []
	for item_id in held:
		piles.append("%d %s" % [held[item_id], _item_name(int(item_id))])
	return "Held by the station: " + ", ".join(piles)


func _recipe_by_id(recipe_id: int) -> CraftableRecipe:
	for skill_id in _recipes_by_skill:
		for recipe in _recipes_by_skill[skill_id]:
			if int(recipe.RecipeId) == recipe_id:
				return recipe
	return null


# --- Pages --------------------------------------------------------------------------------------------------------

func _show_discovery() -> void:
	_make_page.visible = false
	_discover_page.visible = true


func _show_recipes() -> void:
	_discover_page.visible = false
	_make_page.visible = true


# --- Names --------------------------------------------------------------------------------------------------------

## A producing recipe is titled by its output item and the three item-changing effects by what they do; the
## wire carries no names at all.
func _title_of(recipe: CraftableRecipe) -> String:
	if recipe.NeedsTarget:
		return _EFFECT_TITLES.get(recipe.EffectName, recipe.EffectName)

	var name := _item_name(int(recipe.OutputItemId))
	return name if recipe.OutputAmount <= 1 else "%s ×%d" % [name, recipe.OutputAmount]


func _item_name(item_id: int) -> String:
	var item: ItemResource = ItemDB.get_instance().get_item(item_id)
	if item == null:
		printerr("Crafting: unknown item %d" % item_id)
		return "?"
	return tr(item.name_key)


## The two buttons that do something: Discover, which is easy to miss below a long list, and Make.
func _emphasize(button: Button) -> void:
	button.add_theme_stylebox_override("normal", UiStyle.flat(UiStyle.ROW_BG, 4, 8, UiStyle.ACCENT, 1))
	button.add_theme_stylebox_override("hover", UiStyle.flat(UiStyle.ROW_HOVER_BG, 4, 8, UiStyle.ACCENT, 1))
	button.add_theme_color_override("font_color", UiStyle.ACCENT)
	button.add_theme_color_override("font_hover_color", UiStyle.ACCENT)


func _clear(container: Node) -> void:
	for child in container.get_children():
		container.remove_child(child)
		child.queue_free()
