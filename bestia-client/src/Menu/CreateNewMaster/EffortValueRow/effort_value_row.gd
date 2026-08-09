extends HBoxContainer
class_name EffortValueRow

## One attribute on the master creation screen's effort value allocator.
##
## Deliberately not the in-game Game/UI/StatusPoints/StatusRow: that one is plus-only, and it shows a
## server-confirmed value plus a locally buffered pending spend, because in-game a point is gone the
## moment the server accepts it. Nothing here is committed until the character is created, so this row
## owns a single freely editable value that can go back down.

## Emitted whenever the player changes this row's value, so the owning screen can re-total the budget
## and re-gate every row (a row does not know what the others have spent).
signal value_changed(row)

@export var attribute: StatusAttribute.Attribute = StatusAttribute.Attribute.STRENGTH

@onready var _attribute_label: Label = %AttributeLabel
@onready var _minus_button: Button = %MinusButton
@onready var _value_label: Label = %ValueLabel
@onready var _plus_button: Button = %PlusButton
@onready var _cost_label: Label = %CostLabel

## The attribute's current value. Never written directly from outside - the owning screen drives this
## through set_value() so it can re-total the budget in the same pass.
var value: int = 1

# Gates pushed in by the owning screen: whether the budget still covers this row's next point, and
# whether it is above the floor. A row cannot work either out on its own.
var _can_increase: bool = false
var _can_decrease: bool = false


func _ready() -> void:
	_attribute_label.text = StatusAttribute.short_code(attribute)
	_refresh_display()


## The value one "+" would buy, i.e. what step_cost() has to be asked about.
func next_value() -> int:
	return value + 1


func set_value(new_value: int) -> void:
	value = new_value
	_refresh_display()


## Called by the owning screen whenever the shared budget changes, since a row has no standing
## connection to it.
func set_steppable(can_increase: bool, can_decrease: bool) -> void:
	_can_increase = can_increase
	_can_decrease = can_decrease
	_refresh_display()


func _on_minus_button_pressed() -> void:
	if not _can_decrease:
		return
	value -= 1
	_refresh_display()
	value_changed.emit(self)


func _on_plus_button_pressed() -> void:
	if not _can_increase:
		return
	value += 1
	_refresh_display()
	value_changed.emit(self)


func _refresh_display() -> void:
	_value_label.text = "%s" % value
	_minus_button.disabled = not _can_decrease
	_plus_button.disabled = not _can_increase
	# What the next point would cost, so the escalating curve is visible before spending rather than
	# only as a budget that suddenly drains faster.
	_cost_label.text = "(%s)" % StatusAttribute.step_cost(next_value())
