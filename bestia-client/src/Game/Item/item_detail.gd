class_name ItemDetail
## Writes an item out for a reader, in BBCode.
##
## One renderer for every place that describes an item: the inventory, trade and equipment tooltips, which have
## a copy of the item in hand, and the chat popup, which knows only the kind. The per-copy arguments default to
## what "no copy in particular" means, so the kind-only caller passes nothing rather than a null to unpack - a
## [param max_durability] of 0 already means "does not wear", an [param item_level] of 0 "the kind's level",
## and the others "none" and "untouched". An empty [param crafted_by] means nobody crafted the copy.
##
## Static, so the lookups go through [TranslationServer] for the reason [DialogText] gives.


const EMPTY_SOCKET := "○"
const FILLED_SOCKET := "●"


## The item's name, what it takes to use it, the state of this copy, and finally what it is for.
static func as_bbcode(
	item: ItemResource,
	upgrade_level: int = 0,
	durability: int = 0,
	max_durability: int = 0,
	slots: int = 0,
	item_level: int = 0,
	rolled_stats: Array = [],
	crafted_by: String = "",
	crafted_in: String = "",
	own_name: String = "",
	essences: Array = [],
	held_essence: Variant = null
) -> String:
	var title := "[b]%s[/b]" % (own_name if not own_name.is_empty() else _translate(item.name_key))
	if upgrade_level > 0:
		title += " +%d" % upgrade_level

	var lines: Array[String] = [title]

	# Tier 1 is the floor and says nothing about an item, so it is left off rather than shown as "Lv. 1".
	var level := item_level if item_level > 0 else item.level
	if level > 1:
		lines.append("Lv. %d" % level)

	# What it takes to wear it, which is not the item level: see ItemResource.min_level.
	if item.min_level > 1:
		lines.append(_translate("ITEM_MIN_LEVEL").format({"level": item.min_level}))

	# What decides which professions may use it: the weapon type, or the weight of the armor.
	if not item.weapon_type.is_empty():
		lines.append(_translate("ITEM_WEAPON_%s" % item.weapon_type))
	elif item.armor_type > 0:
		lines.append(_translate("ITEM_ARMOR_TYPE_%d" % item.armor_type))

	if item.attack_range > 0:
		lines.append(_translate("ITEM_ATTACK_RANGE").format({"tiles": item.attack_range}))

	for rolled in rolled_stats:
		lines.append(stat_line(rolled.Stat, rolled.Value))

	if max_durability > 0 and item.gives_light():
		# A light's wear is the time it has left to burn, which reads better as a share than as points.
		lines.append("Light left: %d%%" % roundi(100.0 * durability / max_durability))
	elif max_durability > 0:
		var broken := " (broken)" if durability <= 0 else ""
		lines.append("Durability %d/%d%s" % [durability, max_durability, broken])

	if slots > 0:
		lines.append(_translate("ITEM_SOCKETS").format({"rings": sockets_line(slots, essences.size())}))
		for essence in essences:
			lines.append("%s %s" % [FILLED_SOCKET, essence_name(essence)])

	if held_essence != null:
		lines.append(_translate("ITEM_HOLDS_ESSENCE").format({"essence": essence_name(held_essence)}))

	var material_axes := CraftAxes.describe(item.item_id)
	if not material_axes.is_empty():
		lines.append(_translate("ITEM_MATERIAL_AXES").format({"axes": material_axes}))

	if not crafted_by.is_empty():
		lines.append(_translate("ITEM_CRAFTED_BY").format({"crafter": crafted_by, "world": crafted_in}))

	var description := description_of(item)
	if not description.is_empty():
		lines.append("")
		lines.append(description)

	return "\n".join(lines)


## One ring per socket, as the detail column draws them: the [param filled] ones first.
static func sockets_line(slots: int, filled: int = 0) -> String:
	var rings: Array[String] = []
	for i in slots:
		rings.append(FILLED_SOCKET if i < filled else EMPTY_SOCKET)
	return " ".join(rings)


## "Burrow Boar · Ruby" for an [code]ItemEssence[/code]: the species sets the effect, the gem its strength.
static func essence_name(essence) -> String:
	var bestia := BestiaDB.get_instance().get_bestia(int(essence.BestiaId))
	var species := _translate(bestia.name_key) if bestia != null else str(essence.BestiaId)
	return _translate("ITEM_ESSENCE").format({"bestia": species, "gem": _translate("GEM_TIER_" + str(essence.Tier))})


## The prose from [code]items.csv[/code], or nothing for an item that has none.
##
## Safe to hand to a [RichTextLabel] unescaped because those rows are plain prose - no row contains a
## [code][/code] at all, which [code]ItemDescriptionTest[/code] holds them to.
static func description_of(item: ItemResource) -> String:
	if item.description_key.is_empty():
		return ""

	# The lookup echoes the key back when there is no row for it, which is how "not authored yet" is told
	# apart from a real description - the same test [DialogText] uses for an absent title.
	var translated := _translate(item.description_key)
	return "" if translated == item.description_key else translated


## [code]general.csv[/code] holds one [code]ITEM_STAT_<NAME>[/code] row per stat, with a
## [code]{value}[/code] placeholder so a language can put the number where it belongs.
static func stat_line(stat: String, value: int) -> String:
	var key := "ITEM_STAT_" + stat
	var template := _translate(key)
	if template == key:
		template = "{value} " + stat
	return template.format({"value": "%+d" % value})


static func _translate(key: String) -> String:
	return TranslationServer.translate(key)
