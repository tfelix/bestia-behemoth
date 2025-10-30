extends Resource
class_name ItemResource

enum ItemType {USABLE, EQUIP, ETC}

@export var item_id: int
@export var icon: Texture2D
@export var name: String
@export var description: String
@export var weight: int
@export var item_script: GDScript
@export var type: ItemType
