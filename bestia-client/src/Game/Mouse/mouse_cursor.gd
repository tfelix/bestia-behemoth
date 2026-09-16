class_name MouseCursor
extends RefCounted

## The cursor art for each DefaultAction, with the hotspot that puts the icon's own point under the
## click.
##
## Hotspots are in the 32x32 the icons import at - `process/size_limit` in each .import halves the
## 64x64 sources - so they are half the coordinates an image editor shows.

const _POINT := preload("res://Game/Mouse/Icons/hand_point.png")
const _TALK := preload("res://Game/Mouse/Icons/message_dots_square.png")
const _SWORD := preload("res://Game/Mouse/Icons/tool_sword_a.png")
const _HAND_OPEN := preload("res://Game/Mouse/Icons/hand_open.png")
const _HAND_CLOSED := preload("res://Game/Mouse/Icons/hand_closed.png")
const _PICKAXE := preload("res://Game/Mouse/Icons/tool_pickaxe.png")
const _AXE := preload("res://Game/Mouse/Icons/tool_axe.png")
const _HAMMER := preload("res://Game/Mouse/Icons/tool_hammer.png")

## Fingertip, and the cursor for anything with no action of its own.
const _POINTING := {&"texture": _POINT, &"hotspot": Vector2(10, 5)}

## Palm centre, shared by both hands so that closing one does not shift the pointer.
const _OPEN_HAND := {&"texture": _HAND_OPEN, &"hotspot": Vector2(16, 16)}
const _CLOSED_HAND := {&"texture": _HAND_CLOSED, &"hotspot": Vector2(16, 16)}

const _HAMMERING := {&"texture": _HAMMER, &"hotspot": Vector2(7, 9)}

const _BY_ACTION := {
	DefaultAction.Kind.NONE: _POINTING,
	DefaultAction.Kind.SELECT: _POINTING,
	# The bubble's tail, which tapers to the centre of the icon and points straight down, so it lands
	# on whoever is being spoken to.
	DefaultAction.Kind.TALK: {&"texture": _TALK, &"hotspot": Vector2(16, 28)},
	DefaultAction.Kind.ATTACK: {&"texture": _SWORD, &"hotspot": Vector2(5, 5)},
	DefaultAction.Kind.LOOT: _OPEN_HAND,
	DefaultAction.Kind.COLLECT: _OPEN_HAND,
	DefaultAction.Kind.MINE: {&"texture": _PICKAXE, &"hotspot": Vector2(10, 7)},
	DefaultAction.Kind.CHOP: {&"texture": _AXE, &"hotspot": Vector2(6, 10)},
	DefaultAction.Kind.BUILD: _HAMMERING,
	DefaultAction.Kind.USE_STATION: _HAMMERING,
}


## The texture and hotspot for [param action], as `{texture, hotspot}`. [param pressed] closes the
## hand on the two actions that are a grab; every other cursor holds still under the button.
static func resolve(action: int, pressed: bool) -> Dictionary:
	if pressed and (action == DefaultAction.Kind.LOOT or action == DefaultAction.Kind.COLLECT):
		return _CLOSED_HAND

	return _BY_ACTION.get(action, _POINTING)
