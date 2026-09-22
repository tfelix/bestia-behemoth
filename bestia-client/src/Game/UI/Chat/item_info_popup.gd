extends PanelContainer
class_name ItemInfoPopup
## What an item is, beside the cursor, while the player holds it over a name in the chat.
##
## A [PanelContainer] rather than a [PopupPanel] like the map's popups: a PopupPanel is a real window, so it
## would take focus away from a half-typed chat message and spend the player's next click closing itself.
##
## Click-through, which is not cosmetic. A panel that stopped the mouse and opened under the cursor would take
## the hover off the chat line, which ends the hover, which hides the panel, which gives the hover back.

## Clear of the cursor, so the panel does not cover the word that opened it.
const _CURSOR_OFFSET := Vector2(12, 12)

## How close to a screen edge the panel may come. The chat sits bottom left, so one that only ever opened
## down and to the right would open mostly off screen.
const _SCREEN_MARGIN := 8.0

@onready var _text: RichTextLabel = %Text


## Shows [param item] as a kind - the chat line names a kind of item, not one copy of it, so there is no
## wear or upgrade level to report.
func show_for(item: ItemResource) -> void:
	_text.text = ItemDetail.as_bbcode(item)
	show()
	_place_beside_cursor()


func _place_beside_cursor() -> void:
	# The panel is sized by the text just put into it, and that size is what the clamp works against - so
	# both wait for the layout pass that measures the wrapped description.
	await get_tree().process_frame
	reset_size()

	var margin := Vector2(_SCREEN_MARGIN, _SCREEN_MARGIN)
	var limit := get_viewport_rect().size - size - margin
	global_position = (get_global_mouse_position() + _CURSOR_OFFSET).clamp(margin, limit)
