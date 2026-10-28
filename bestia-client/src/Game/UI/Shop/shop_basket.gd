class_name ShopBasket
extends RefCounted

## What the player has picked to buy and to sell at one counter.
##
## Kept in the order it was picked, which is also the order of the lines on the wire - so it is how the coins of
## a ShopQuoteSMSG are matched back to their lines. An item is on one side only: buying and selling the same
## good in one trade would only lose the spread twice, and the server refuses it.

var _amounts: Dictionary[int, int] = {}
var _selling: Dictionary[int, bool] = {}


## Sets the amount on [param selling]'s side; 0 or less takes the line out.
func set_amount(item_id: int, selling: bool, amount: int) -> void:
	if amount <= 0:
		_amounts.erase(item_id)
		_selling.erase(item_id)
		return

	_amounts[item_id] = amount
	_selling[item_id] = selling


func amount_of(item_id: int, selling: bool) -> int:
	if _selling.get(item_id, selling) != selling:
		return 0

	return _amounts.get(item_id, 0)


## Whether [param item_id] is already on the other side than [param selling].
func is_on_other_side(item_id: int, selling: bool) -> bool:
	return _selling.has(item_id) and _selling[item_id] != selling


func is_empty() -> bool:
	return _amounts.is_empty()


func clear() -> void:
	_amounts.clear()
	_selling.clear()


## Item ids in basket order, on [param selling]'s side only.
func items_on(selling: bool) -> Array[int]:
	var ids: Array[int] = []
	for item_id in _amounts:
		if _selling[item_id] == selling:
			ids.append(item_id)

	return ids


## How many of each item is being sold.
func sold() -> Dictionary[int, int]:
	var amounts: Dictionary[int, int] = {}
	for item_id in items_on(true):
		amounts[item_id] = _amounts[item_id]

	return amounts


func lines() -> Array[ShopLine]:
	var lines: Array[ShopLine] = []
	for item_id in _amounts:
		var line := ShopLine.new()
		line.ItemId = item_id
		line.Amount = _amounts[item_id]
		line.Selling = _selling[item_id]
		lines.append(line)

	return lines


## The index of [param item_id]'s line in [method lines], or -1.
func line_index_of(item_id: int) -> int:
	return _amounts.keys().find(item_id)
