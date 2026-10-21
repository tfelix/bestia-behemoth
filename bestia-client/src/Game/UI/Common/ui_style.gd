class_name UiStyle
extends RefCounted

## The look the party and guild windows share, in code rather than in scenes: both build their rows at runtime,
## one per member, so a scene per row would only be a second copy of what these make.
##
## The panel and title colours match [WidgetWindow]'s chrome, so the windows sit beside the older ones.

const PANEL_BG := Color(0.11, 0.11, 0.13)
const ROW_BG := Color(0.15, 0.15, 0.18)
const ROW_HOVER_BG := Color(0.18, 0.18, 0.22)
## The player's own row, so it is found at a glance in a long list.
const SELF_BORDER := Color(0.36, 0.72, 0.68)
const BAR_BG := Color(0.06, 0.06, 0.07)
const HP_FILL := Color(0.86, 0.30, 0.30)
const MANA_FILL := Color(0.33, 0.56, 0.96)
const EXP_FILL := Color(0.95, 0.78, 0.35)
const TEXT := Color(0.92, 0.92, 0.94)
const MUTED := Color(0.60, 0.60, 0.66)
## The owner's and the leader's mark.
const ACCENT := Color(1.0, 0.82, 0.35)
const ONLINE := Color(0.45, 0.85, 0.45)
const OFFLINE := Color(0.40, 0.40, 0.44)
const DANGER := Color(0.90, 0.38, 0.38)


static func flat(bg: Color, radius: int = 4, margin: int = 6, border_color: Color = Color.TRANSPARENT, border: int = 0) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = bg
	box.set_corner_radius_all(radius)
	box.content_margin_left = margin
	box.content_margin_right = margin
	box.content_margin_top = maxi(margin - 2, 0)
	box.content_margin_bottom = maxi(margin - 2, 0)
	if border > 0:
		box.border_color = border_color
		box.set_border_width_all(border)
	return box


## A thin bar with no number on it; the exact values go in its tooltip, see [method set_bar].
static func bar(fill: Color, height: int) -> ProgressBar:
	var progress := ProgressBar.new()
	progress.show_percentage = false
	progress.custom_minimum_size = Vector2(0, height)
	progress.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	progress.mouse_filter = Control.MOUSE_FILTER_PASS

	var background := flat(BAR_BG, 2, 0)
	var filled := flat(fill, 2, 0)
	progress.add_theme_stylebox_override("background", background)
	progress.add_theme_stylebox_override("fill", filled)
	return progress


## A maximum of 0 would draw the bar full, so an unknown pool reads as empty.
static func set_bar(progress: ProgressBar, current: int, maximum: int) -> void:
	progress.max_value = maxi(maximum, 1)
	progress.value = clampi(current, 0, maxi(maximum, 0))
	progress.tooltip_text = "%d / %d" % [current, maximum]


static func label(text: String = "", font_size: int = 13, color: Color = TEXT) -> Label:
	var lbl := Label.new()
	lbl.text = text
	lbl.add_theme_font_size_override("font_size", font_size)
	lbl.add_theme_color_override("font_color", color)
	lbl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	return lbl


## A small round light: online or not.
static func dot(color: Color) -> Panel:
	var light := Panel.new()
	light.custom_minimum_size = Vector2(8, 8)
	light.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	light.mouse_filter = Control.MOUSE_FILTER_PASS
	light.add_theme_stylebox_override("panel", flat(color, 4, 0))
	return light


static func set_dot(light: Panel, color: Color) -> void:
	light.add_theme_stylebox_override("panel", flat(color, 4, 0))


## A flat, square button for a single glyph, such as the kick cross.
static func glyph_button(glyph: String, tooltip: String, color: Color = MUTED) -> Button:
	var button := Button.new()
	button.text = glyph
	button.flat = true
	button.tooltip_text = tooltip
	button.custom_minimum_size = Vector2(20, 20)
	button.focus_mode = Control.FOCUS_NONE
	button.add_theme_color_override("font_color", color)
	button.add_theme_color_override("font_hover_color", DANGER)
	button.add_theme_font_size_override("font_size", 13)
	return button


static func margin(child: Control, left: int, top: int, right: int, bottom: int) -> MarginContainer:
	var container := MarginContainer.new()
	container.add_theme_constant_override("margin_left", left)
	container.add_theme_constant_override("margin_top", top)
	container.add_theme_constant_override("margin_right", right)
	container.add_theme_constant_override("margin_bottom", bottom)
	container.add_child(child)
	return container


static func hbox(separation: int = 6) -> HBoxContainer:
	var box := HBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	return box


static func vbox(separation: int = 6) -> VBoxContainer:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	return box


static func spacer() -> Control:
	var space := Control.new()
	space.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	space.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return space


## English grouping, the only locale the client ships: 1067 reads 1,067.
static func group_digits(amount: int) -> String:
	var digits := str(amount)
	var grouped := ""
	while digits.length() > 3:
		grouped = "," + digits.right(3) + grouped
		digits = digits.left(-3)
	return digits + grouped
