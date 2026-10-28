class_name OwnedSlots
## What the party panel and the map agree on about the player's own entities: one colour per slot, so a row
## and a dot on the map read as the same creature.
##
## Slot 0 is the master, slot n the n-th bestia in the order [method EntityManager.get_owned_entity_ids] lists.

## The map's own player marker colour, so driving the master looks as it always did.
const MASTER_COLOR := Color(1.0, 0.86, 0.35)
const EMPTY_COLOR := Color(0.45, 0.45, 0.45)

## Saturated, and kept clear of the pale blue of the player's map marks.
const _BESTIA_COLORS: Array[Color] = [
	Color(1.0, 0.45, 0.25),
	Color(0.95, 0.35, 0.85),
	Color(0.6, 1.0, 0.3),
	Color(0.6, 0.45, 1.0),
	Color(0.2, 0.85, 0.7),
	Color(1.0, 0.65, 0.75),
	Color(0.95, 0.95, 0.95),
	Color(0.85, 0.15, 0.3),
]


static func color_of(slot: int) -> Color:
	if slot == 0:
		return MASTER_COLOR

	return _BESTIA_COLORS[(slot - 1) % _BESTIA_COLORS.size()]


## Draws [param button]'s icon in a slot colour in every state but disabled, leaving its frame the theme's.
static func tint_icon(button: Button, color: Color) -> void:
	for state in ["icon_normal_color", "icon_hover_color", "icon_pressed_color", "icon_hover_pressed_color", "icon_focus_color"]:
		button.add_theme_color_override(state, color)


## A bestia the player has not named goes by its species.
static func name_of(bestia: BestiaInfo) -> String:
	if bestia.Name != "":
		return bestia.Name

	var species := BestiaDB.get_instance().get_bestia(bestia.MobId)
	return str(TranslationServer.translate(species.name_key)) if species != null else "?"
