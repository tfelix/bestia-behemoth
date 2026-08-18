extends MarginContainer
## One line of the crafting window: what a recipe makes, what it costs, and how likely it is to work.
##
## Everything shown here is resolved from the server message plus the client's own item DB - the wire
## carries no names at all, so a producing recipe is titled by its output item and the three
## item-changing effects by what they do. See [CraftableRecipesSMSG].

## Emitted when the player asks for this recipe. The window decides what to do with it, because a
## targeted recipe also needs a held item picked and that selector does not live on the row.
signal craft_requested(recipe_id: int)

## What the three item-changing effects are called, keyed by [member CraftableRecipe.EffectName].
## Hardcoded English for now, like the rest of this window - none of it has a localization row yet, and
## inventing keys for text nobody has translated would look finished without being.
const _EFFECT_TITLES := {
	"add_slot": "Cut a rune slot",
	"upgrade": "Upgrade equipment",
	"repair": "Repair equipment",
}

const _AFFORDABLE_COLOR := Color(0.82, 0.82, 0.85)
const _UNAFFORDABLE_COLOR := Color(0.79, 0.42, 0.4)

@onready var _recipe_name: Label = %RecipeName
@onready var _materials: Label = %Materials
@onready var _odds: Label = %Odds
@onready var _craft_button: Button = %CraftButton

var _recipe: CraftableRecipe = null


func _ready() -> void:
	_craft_button.pressed.connect(_on_craft_pressed)


func initialize(recipe: CraftableRecipe) -> void:
	_recipe = recipe

	_recipe_name.text = _title_of(recipe)
	_materials.text = _materials_of(recipe)
	_materials.add_theme_color_override(
		"font_color", _AFFORDABLE_COLOR if recipe.InputsHeld else _UNAFFORDABLE_COLOR
	)
	_odds.text = "%d%% · %.1fs" % [roundi(recipe.SuccessPermille / 10.0), recipe.CraftMillis / 1000.0]


func needs_target() -> bool:
	return _recipe != null and _recipe.NeedsTarget


## Greyed out while a craft is already running, since the server refuses a second one anyway, and while
## a targeted recipe has nothing picked to work on.
func set_can_craft(can_craft: bool) -> void:
	_craft_button.disabled = not can_craft


func _title_of(recipe: CraftableRecipe) -> String:
	if recipe.NeedsTarget:
		return _EFFECT_TITLES.get(recipe.EffectName, recipe.EffectName)

	var item: ItemResource = ItemDB.get_instance().get_item(int(recipe.OutputItemId))
	if item == null:
		printerr("CraftingRow: unknown output item %d for recipe %d" % [recipe.OutputItemId, recipe.RecipeId])
		return "?"

	var name := tr(item.name_key)
	return name if recipe.OutputAmount <= 1 else "%s ×%d" % [name, recipe.OutputAmount]


## "2/3 Iron Ore, 1/1 Coal" - held first so a shortfall reads as a shortfall at a glance.
func _materials_of(recipe: CraftableRecipe) -> String:
	var parts: Array[String] = []

	for input in recipe.Inputs:
		var item: ItemResource = ItemDB.get_instance().get_item(int(input.ItemId))
		var label := tr(item.name_key) if item else "?"
		parts.append("%d/%d %s" % [input.Held, input.Amount, label])

	return ", ".join(parts) if not parts.is_empty() else "No materials"


func _on_craft_pressed() -> void:
	if _recipe != null:
		craft_requested.emit(int(_recipe.RecipeId))
