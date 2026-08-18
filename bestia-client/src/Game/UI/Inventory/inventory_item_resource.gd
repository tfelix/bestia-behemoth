extends Resource
class_name InventoryItemResource


@export var amount: int = 1
@export var player_item_id: int
@export var item: ItemResource
@export var equipped: bool = false

## Wear on the backing instance. Both zero for a plain stack and for gear that does not wear, which is
## what [InventoryItem] tests to decide whether to draw a bar at all.
@export var durability: int = 0
@export var max_durability: int = 0

## Rune slots cut into the backing instance by Item Customization.
@export var slots: int = 0
