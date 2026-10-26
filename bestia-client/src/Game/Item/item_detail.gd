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
	crafted_in: String = ""
) -> String:
	var title := "[b]%s[/b]" % _translate(item.name_key)
	if upgrade_level > 0:
		title += " +%d" % upgrade_level

	var lines: Array[String] = [title]

	# Tier 1 is the floor and says nothing about an item, so it is left off rather than shown as "Lv. 1".
	var level := item_level if item_level > 0 else item.level
	if level > 1:
		lines.append("Lv. %d" % level)

	for rolled in rolled_stats:
		lines.append(_stat_line(rolled.Stat, rolled.Value))

	if max_durability > 0:
		var broken := " (broken)" if durability <= 0 else ""
		lines.append("Durability %d/%d%s" % [durability, max_durability, broken])

	if slots > 0:
		lines.append("%d socket(s)" % slots)

	var material_axes := CraftAxes.describe(item.item_id)
	if not material_axes.is_empty():
		lines.append(_translate("ITEM_MATERIAL_AXES").format({"axes": material_axes}))

	if not crafted_by.is_empty():
		lines.append(_translate("ITEM_CRAFTED_BY").format({"crafter": crafted_by, "world": crafted_in}))

	var description := _description_of(item)
	if not description.is_empty():
		lines.append("")
		lines.append(description)

	return "\n".join(lines)


## The prose from [code]items.csv[/code], or nothing for an item that has none.
##
## Safe to hand to a [RichTextLabel] unescaped because those rows are plain prose - no row contains a
## [code][/code] at all, which [code]ItemDescriptionTest[/code] holds them to.
static func _description_of(item: ItemResource) -> String:
	if item.description_key.is_empty():
		return ""

	# The lookup echoes the key back when there is no row for it, which is how "not authored yet" is told
	# apart from a real description - the same test [DialogText] uses for an absent title.
	var translated := _translate(item.description_key)
	return "" if translated == item.description_key else translated


## [code]general.csv[/code] holds one [code]ITEM_STAT_<NAME>[/code] row per stat, with a
## [code]{value}[/code] placeholder so a language can put the number where it belongs.
static func _stat_line(stat: String, value: int) -> String:
	var key := "ITEM_STAT_" + stat
	var template := _translate(key)
	if template == key:
		template = "{value} " + stat
	return template.format({"value": "%+d" % value})


static func _translate(key: String) -> String:
	return TranslationServer.translate(key)
