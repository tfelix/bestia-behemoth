extends Resource
class_name ShortcutData

enum ShortcutType { NONE, ITEM, ATTACK }

@export var type: ShortcutType = ShortcutType.NONE
@export var reference_id: int = -1  # item_id or attack_id
@export var custom_script: GDScript  # Optional script for specialized behavior


func is_empty() -> bool:
	return type == ShortcutType.NONE or reference_id == -1


func clear() -> void:
	type = ShortcutType.NONE
	reference_id = -1
	custom_script = null


func to_dict() -> Dictionary:
	return {
		"type": ShortcutType.keys()[type],
		"reference_id": reference_id,
		"custom_script": custom_script.resource_path if custom_script else ""
	}


static func from_dict(data: Dictionary) -> ShortcutData:
	var shortcut := ShortcutData.new()

	if data.has("type"):
		var type_str = data["type"]
		shortcut.type = ShortcutType.get(type_str) if type_str in ShortcutType.keys() else ShortcutType.NONE

	if data.has("reference_id"):
		shortcut.reference_id = data["reference_id"]

	if data.has("custom_script") and data["custom_script"] != "":
		shortcut.custom_script = load(data["custom_script"])

	return shortcut
