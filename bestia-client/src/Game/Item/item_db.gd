extends Node
class_name ItemDB

var items := {}

func _ready():
	var dir := DirAccess.open("res://Game/Item/Items")
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var item = load("res://Game/Item/Items/" + file_name)
				if item and item.has_method("get_item_id"):
					items[item.get_item_id()] = item
				elif item and "item_id" in item:
					items[item.item_id] = item
			file_name = dir.get_next()
		dir.list_dir_end()

func get_item(item_id: int) -> ItemResource:
	return items.get(item_id, null)
