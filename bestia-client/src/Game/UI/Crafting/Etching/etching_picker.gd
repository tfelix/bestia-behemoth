extends VBoxContainer
class_name EtchingPicker
## The Bestia half of a Runic Etching recipe: which Bestia goes into the gem, and what comes out. Lists the Bestias
## locked into this Carving Table, the Artificer's own and other masters'. The Essence Gem goes to the owner by post.
## English like the rest of the crafting window.

signal picked()

## The Artificer calls off their own etching of this Bestia.
signal call_off_requested(player_bestia_id: int)

## The server's floor on any craft chance, in percent.
const _MIN_CHANCE := 1
const _PENALTY_PER_LEVEL := 5
const _NO_LEVEL_TOO_HIGH := 1000

## The player bestia id of the picked Bestia, or 0.
var picked_id: int = 0

## [DepositedBestia]s: every Bestia locked into this table, from the last recipe list.
var deposited: Array = []

var _recipe: CraftableRecipe = null


func show_for(recipe: CraftableRecipe) -> void:
	if recipe != _recipe:
		picked_id = 0
	_recipe = recipe
	_rebuild()


## Whether the picked Bestia may be etched now: one is picked and nobody etches it yet.
func can_etch() -> bool:
	var chosen := _picked_bestia()
	return chosen != null and int(chosen.EtchSecondsLeft) == 0 and _why_not(chosen, _tier()).is_empty()


func _rebuild() -> void:
	for child in get_children():
		remove_child(child)
		child.queue_free()

	var tier := _tier()
	add_child(UiStyle.label("Pick a Bestia", 13))
	if deposited.is_empty():
		add_child(UiStyle.label("No Bestia is locked into this table yet. Its owner locks it in from the tab above.", 12, UiStyle.MUTED))
	for bestia in deposited:
		add_child(_row(bestia, tier))

	var chosen := _picked_bestia()
	if chosen == null:
		return

	add_child(HSeparator.new())
	if int(chosen.EtchSecondsLeft) > 0:
		_add_running(chosen)
		return

	add_child(_result(chosen, tier))
	add_child(UiStyle.label("%s  ·  takes %s" % [_odds_text(int(chosen.Level)), duration_text(int(chosen.EtchSeconds))], 12, UiStyle.MUTED))
	add_child(UiStyle.label("Success: the Bestia is gone.  Failure: only the gem shatters.", 12, UiStyle.ACCENT))
	add_child(UiStyle.label("The etching runs on its own. The Essence Gem goes to %s by post." % _owner_of(chosen), 12, UiStyle.ACCENT))


func _add_running(bestia: DepositedBestia) -> void:
	add_child(UiStyle.label("%s etches it: %s left." % [bestia.EtcherName, duration_text(int(bestia.EtchSecondsLeft))], 13))
	if not bestia.EtchingMine:
		return

	var call_off := Button.new()
	call_off.text = "Call off"
	call_off.focus_mode = Control.FOCUS_NONE
	call_off.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	call_off.pressed.connect(func() -> void: call_off_requested.emit(int(bestia.PlayerBestiaId)))
	add_child(call_off)
	add_child(UiStyle.label("Your gem comes back by post.", 12, UiStyle.MUTED))


func _row(bestia: DepositedBestia, tier: String) -> Button:
	var why_not := _why_not(bestia, tier)
	var etching := int(bestia.EtchSecondsLeft) > 0
	var row := Button.new()
	row.toggle_mode = true
	row.focus_mode = Control.FOCUS_NONE
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.add_theme_font_size_override("font_size", 13)
	# A Bestia under way stays pickable for the one who etches it, so they can call it off.
	row.disabled = (etching and not bestia.EtchingMine) or (not etching and not why_not.is_empty())
	row.button_pressed = int(bestia.PlayerBestiaId) == picked_id
	row.text = "%s   Lv %d" % [name_of(bestia), int(bestia.Level)]
	if not bestia.Mine:
		row.text += "   · %s's" % bestia.OwnerName
	if etching:
		row.text += "   · being etched, %s left" % duration_text(int(bestia.EtchSecondsLeft))
	elif not why_not.is_empty():
		row.text += "   · " + why_not
	row.pressed.connect(_on_row_pressed.bind(int(bestia.PlayerBestiaId)))
	return row


func _on_row_pressed(player_bestia_id: int) -> void:
	picked_id = player_bestia_id
	_rebuild()
	picked.emit()


## Empty when the Bestia may go into this gem.
func _why_not(bestia: DepositedBestia, tier: String) -> String:
	var species := BestiaDB.get_instance().get_bestia(int(bestia.MobId))
	if species == null or species.essence_effect.is_empty():
		return "has no essence"
	var boss_tier := EssenceText.boss_tier(species)
	if not boss_tier.is_empty() and boss_tier != tier:
		return "fills only %s" % TranslationServer.translate("GEM_TIER_" + boss_tier)
	if int(bestia.Level) < int(EssenceText.MIN_BESTIA_LEVEL_BY_TIER.get(tier, 0)):
		return "too low for %s" % TranslationServer.translate("GEM_TIER_" + tier)
	return ""


func _result(bestia: DepositedBestia, tier: String) -> VBoxContainer:
	var box := UiStyle.vbox(2)
	var essence := {"BestiaId": int(bestia.MobId), "Tier": tier}
	box.add_child(UiStyle.label("Essence Gem: " + ItemDetail.essence_line(essence), 13, UiStyle.SELF_BORDER))

	var species := BestiaDB.get_instance().get_bestia(int(bestia.MobId))
	var min_item_level := EssenceText.min_item_level(species, tier)
	var tool: String = EssenceText.tool_of(species)
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


func _picked_bestia() -> DepositedBestia:
	for bestia in deposited:
		if int(bestia.PlayerBestiaId) == picked_id:
			return bestia
	return null


func _owner_of(bestia: DepositedBestia) -> String:
	return "you" if bestia.Mine else bestia.OwnerName


## Its own name, or the species name. Takes a [DepositedBestia] or a [DepositableBestia].
static func name_of(bestia) -> String:
	if not bestia.Name.is_empty():
		return bestia.Name
	var species := BestiaDB.get_instance().get_bestia(int(bestia.MobId))
	return TranslationServer.translate(species.name_key) if species != null else "?"


## "2 h 10 min", "34 min" or "45 s".
static func duration_text(seconds: int) -> String:
	if seconds >= 3600:
		return "%d h %d min" % [seconds / 3600, (seconds % 3600) / 60]
	if seconds >= 60:
		return "%d min" % ceili(seconds / 60.0)
	return "%d s" % seconds
