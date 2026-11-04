class_name SelectionMarker
extends Sprite3D

## The arrow over [member MouseManager.selected_entity], pointing down at its name tag. Follows the tag rather
## than the body: the tag is already placed over each creature's head and shows exactly while it is selected.

const _NAME_TAG := "NameTag"


func _process(_delta: float) -> void:
	var label := _selected_name_tag()
	visible = label != null
	if not visible:
		return

	global_position = label.global_position
	pixel_size = NameTagEmblem.one_texel_per_screen_pixel(get_viewport(), label.pixel_size)
	render_priority = label.render_priority + 1

	# Fixed-size sprites at one spot share one screen anchor, so the arrow is lifted in screen pixels: past the
	# label's lines - a guild member's tag has two - and half its own height.
	var label_to_screen := label.pixel_size / pixel_size
	var font: Font = label.font if label.font != null else ThemeDB.fallback_font
	var lines := label.text.split("\n").size()
	var text_height := lines * font.get_height(label.font_size) * label_to_screen
	offset = Vector2(0, (text_height + texture.get_height()) / 2.0)


## Null while nothing is selected, the selection is gone, or its tag is hidden - as it is for a body on its way
## out of the world.
func _selected_name_tag() -> Label3D:
	var mouse := MouseManager.get_instance()
	if mouse == null or not is_instance_valid(mouse.selected_entity):
		return null

	var label := mouse.selected_entity.get_node_or_null(_NAME_TAG) as Label3D
	if label == null or not label.is_visible_in_tree():
		return null
	return label

