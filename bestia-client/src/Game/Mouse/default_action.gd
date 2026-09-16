class_name DefaultAction
extends RefCounted

## What a left-click on a hovered target would do.
##
## One vocabulary for the click and the cursor: MouseStateDefault decides an action once, acts on it,
## and hands the same value to MouseCursor. A separate table for cursors would drift from the click
## path the first time either grew a case.

enum Kind {
	NONE,
	SELECT,
	ATTACK,
	TALK,
	LOOT,
	COLLECT,
	MINE,
	CHOP,
	BUILD,
	USE_STATION,
}

## The names StaticEntityRenderer writes onto a PropPicker. Names rather than the C# enum's ordinals,
## because nothing anchors that numbering to this one - and a name this table does not know costs a
## plain pointer, where a stale ordinal would confidently show the wrong tool.
const _BY_PROP_NAME := {
	&"collect": Kind.COLLECT,
	&"mine": Kind.MINE,
	&"chop": Kind.CHOP,
	&"use": Kind.USE_STATION,
}


static func from_prop_name(prop_action: StringName) -> int:
	return _BY_PROP_NAME.get(prop_action, Kind.NONE)
