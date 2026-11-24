extends VBoxContainer
class_name EtchingPicker
## The Bestia half of a Runic Etching recipe: which Bestia goes into the gem, and what comes out. Lists the
## Artificer's own Bestias and the ones other masters offered at this table; the gem of an offered one goes to its
## owner.
##
## Only the Bestias out in the world are listed. One in storage has to be called first, since the server wants it
## standing at the Carving Table. English like the rest of the crafting window.

signal picked()

## The server's floor on any craft chance, in percent.
const _MIN_CHANCE := 1
const _PENALTY_PER_LEVEL := 5
const _NO_LEVEL_TOO_HIGH := 1000

## The entity id of the picked Bestia, or 0.
var picked_entity_id: int = 0

## [EtchingOffer]s of other masters at this table, from the last recipe list.
var offers: Array = []

var _recipe: CraftableRecipe = null


func show_for(recipe: CraftableRecipe) -> void:
	if recipe != _recipe:
		picked_entity_id = 0
	_recipe = recipe
	_rebuild()


func _rebuild() -> void:
	for child in get_children():
		remove_child(child)
		child.queue_free()

	var tier := _tier()
	add_child(UiStyle.label("Pick a Bestia", 13))
	var bestias := _candidates()
	if bestias.is_empty():
		add_child(UiStyle.label("Call a Bestia to the Carving Table first.", 12, UiStyle.MUTED))
	for bestia in bestias:
		add_child(_row(bestia, tier))

	var chosen := _picked_bestia(bestias)
	if chosen.is_empty():
		return

	add_child(HSeparator.new())
	add_child(_result(chosen, tier))
	add_child(UiStyle.label(_odds_text(chosen.level), 12, UiStyle.MUTED))
	add_child(UiStyle.label("Success: the Bestia is gone.  Failure: only the gem shatters.", 12, UiStyle.ACCENT))
	if not chosen.owner.is_empty():
		add_child(UiStyle.label("The Essence Gem goes to %s." % chosen.owner, 12, UiStyle.ACCENT))


## Each Bestia as {entity_id, mob_id, level, name, owner}; owner is empty for the Artificer's own.
func _candidates() -> Array[Dictionary]:
	var candidates: Array[Dictionary] = []
	for bestia in _owned_bestias():
		candidates.append({
			"entity_id": int(bestia.EntityId), "mob_id": int(bestia.MobId), "level": int(bestia.Level),
			"name": OwnedSlots.name_of(bestia), "owner": "",
		})
	for offer in offers:
		var species := BestiaDB.get_instance().get_bestia(int(offer.MobId))
		candidates.append({
			"entity_id": int(offer.EntityId), "mob_id": int(offer.MobId), "level": int(offer.Level),
			"name": tr(species.name_key) if species != null else "?", "owner": offer.OwnerName,
		})
	return candidates


func _row(bestia: Dictionary, tier: String) -> Button:
	var why_not := _why_not(bestia, tier)
	var row := Button.new()
	row.toggle_mode = true
	row.focus_mode = Control.FOCUS_NONE
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.add_theme_font_size_override("font_size", 13)
	row.disabled = not why_not.is_empty()
	row.button_pressed = bestia.entity_id == picked_entity_id
	row.text = "%s   Lv %d" % [bestia.name, bestia.level]
	if not bestia.owner.is_empty():
		row.text += "   · %s's" % bestia.owner
	if not why_not.is_empty():
		row.text += "   · " + why_not
	row.pressed.connect(_on_row_pressed.bind(bestia.entity_id))
	return row


func _on_row_pressed(entity_id: int) -> void:
	picked_entity_id = entity_id
	_rebuild()
	picked.emit()


## Empty when the Bestia may go into this gem.
func _why_not(bestia: Dictionary, tier: String) -> String:
	var species := BestiaDB.get_instance().get_bestia(bestia.mob_id)
	if species == null or species.essence_effect.is_empty():
		return "has no essence"
	if bestia.level < int(EssenceText.MIN_BESTIA_LEVEL_BY_TIER.get(tier, 0)):
		return "too low for %s" % TranslationServer.translate("GEM_TIER_" + tier)
	return ""


func _result(bestia: Dictionary, tier: String) -> VBoxContainer:
	var box := UiStyle.vbox(2)
	var essence := {"BestiaId": bestia.mob_id, "Tier": tier}
	box.add_child(UiStyle.label("Essence Gem: " + ItemDetail.essence_line(essence), 13, UiStyle.SELF_BORDER))

	var species := BestiaDB.get_instance().get_bestia(bestia.mob_id)
	var min_item_level := int(EssenceText.MIN_ITEM_LEVEL_BY_TIER.get(tier, 0))
	var tool: String = EssenceText.TOOL_BY_EFFECT.get(species.essence_effect, "")
	var socket_name: String = TranslationServer.translate("TOOL_KIND_" + tool) if not tool.is_empty() else species.essence_group.capitalize()
	var fits := "Fits a %s socket" % socket_name
	if min_item_level > 0:
		fits += " of an item of level %d or more" % min_item_level
	box.add_child(UiStyle.label(fits, 12, UiStyle.MUTED))
	return box


## The server's chance minus five points for each level above the reliable one, as Runic Etching works it out.
func _odds_text(bestia_level: int) -> String:
	var reliable := int(_recipe.ReliableBestiaLevel)
	var levels_above := maxi(0, bestia_level - reliable)
	var chance := maxi(_MIN_CHANCE, roundi(_recipe.SuccessPermille / 10.0) - levels_above * _PENALTY_PER_LEVEL)
	if reliable >= _NO_LEVEL_TOO_HIGH:
		return "Success %d %%" % chance
	return "Success %d %%  ·  reliable up to Bestia Lv %d" % [chance, reliable]


func _tier() -> String:
	if _recipe == null or _recipe.Inputs.is_empty():
		return ""
	return EssenceText.TIER_BY_CUT_GEM.get(int(_recipe.Inputs[0].ItemId), "")


func _picked_bestia(bestias: Array[Dictionary]) -> Dictionary:
	for bestia in bestias:
		if bestia.entity_id == picked_entity_id:
			return bestia
	return {}


func _owned_bestias() -> Array[BestiaInfo]:
	var entities := EntityManager.get_instance()
	return entities.get_owned_bestias() if entities else []
