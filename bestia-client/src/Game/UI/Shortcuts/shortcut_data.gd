extends Resource
class_name ShortcutData

enum ShortcutType { NONE, ITEM, ATTACK }

@export var type: ShortcutType = ShortcutType.NONE
@export var reference_id: int = -1  # item_id or attack_id


func is_empty() -> bool:
	return type == ShortcutType.NONE or reference_id == -1


func clear() -> void:
	type = ShortcutType.NONE
	reference_id = -1


func to_dict() -> Dictionary:
	return {
		"type": ShortcutType.keys()[type],
		"reference_id": reference_id
	}


static func from_dict(data: Dictionary) -> ShortcutData:
	var shortcut := ShortcutData.new()

	if data.has("type"):
		var type_str = data["type"]
		shortcut.type = ShortcutType.get(type_str) if type_str in ShortcutType.keys() else ShortcutType.NONE

	if data.has("reference_id"):
		shortcut.reference_id = data["reference_id"]

	return shortcut
