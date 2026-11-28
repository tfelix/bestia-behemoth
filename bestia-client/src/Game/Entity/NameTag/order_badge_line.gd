class_name OrderBadgeLine

## The Order a master is sworn to, as a tinted line above its name tag, so a rival stands out before a fight.
## A child of the name tag, so it shows and hides with it.

const NONE := 0

## Keyed by the wire value of FactionId.
const _KEYS := {1: "ORDER_BADGE_CHAOS", 2: "ORDER_BADGE_ETERNITY", 3: "ORDER_BADGE_CIRCLE"}
const _COLOURS := {1: Color(0.9, 0.3, 0.25), 2: Color(0.95, 0.82, 0.45), 3: Color(0.55, 0.85, 0.8)}
const _FONT_SIZE_LESS := 2


static func of(msg: FactionBadgeComponentSMSG) -> int:
	if msg == null or msg.Removed:
		return NONE
	return msg.Faction


## @return what to keep as [param line]; made on first use.
static func show_above(name_tag: Label3D, line: Label3D, faction: int) -> Label3D:
	if not _KEYS.has(faction):
		if line != null:
			line.visible = false
		return line

	if line == null:
		line = _line_like(name_tag)
		name_tag.add_child(line)
	line.visible = true
	line.text = name_tag.tr(_KEYS[faction])
	line.modulate = _COLOURS[faction]
	line.offset = Vector2(0, (_height_of(name_tag, name_tag.text.split("\n").size()) + _height_of(line, 1)) / 2.0)
	return line


static func _line_like(name_tag: Label3D) -> Label3D:
	var line := Label3D.new()
	line.pixel_size = name_tag.pixel_size
	line.billboard = name_tag.billboard
	line.no_depth_test = name_tag.no_depth_test
	line.fixed_size = name_tag.fixed_size
	line.texture_filter = name_tag.texture_filter
	line.font = name_tag.font
	line.font_size = maxi(name_tag.font_size - _FONT_SIZE_LESS, 8)
	line.outline_size = name_tag.outline_size
	return line


static func _height_of(label: Label3D, lines: int) -> float:
	var font: Font = label.font if label.font != null else ThemeDB.fallback_font
	return lines * font.get_height(label.font_size)
