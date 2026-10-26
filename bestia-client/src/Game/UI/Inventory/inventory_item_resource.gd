extends Resource
class_name InventoryItemResource


@export var amount: int = 1
## What every action on this held item names it by. Equals [member unique_id] for an instance; a plain pile
## gets a handle from the server that holds while the pile is held.
@export var player_item_id: int

## Id of the backing item instance, or 0 for a plain pile.
@export var unique_id: int = 0
@export var item: ItemResource
@export var equipped: bool = false

## Wear on the backing instance. Both zero for a plain stack and for gear that does not wear, which is
## what [InventoryItem] tests to decide whether to draw a bar at all.
@export var durability: int = 0
@export var max_durability: int = 0

## Sockets of the backing instance, opened at the forge or by Item Customization.
@export var slots: int = 0

## How often this copy has been improved by Upgrade Equipment.
@export var upgrade_level: int = 0

## This copy's level with its stats and sockets counted; 0 when the server sent none.
@export var item_level: int = 0

## What this copy rolled when it was made, as [ItemStatValue]s.
@export var rolled_stats: Array = []
