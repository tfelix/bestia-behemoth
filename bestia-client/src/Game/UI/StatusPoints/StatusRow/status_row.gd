extends HBoxContainer
class_name StatusRow

## Emitted whenever this row's buffered (not-yet-confirmed) point investment changes, so the
## owning StatusPoints list can recompute how many status points are still available to spend
## across all rows and whether the footer's Confirm/Cancel buttons should show.
signal investment_changed(row)

@export var attribute: StatusAttribute.Attribute = StatusAttribute.Attribute.STRENGTH

@onready var _attribute_label: Label = %AttributeLabel
@onready var _value_label: Label = %ValueLabel
@onready var _spend_point_button: Button = %SpendPointButton

# The value the server last confirmed (from the last StatusValuesComponentSMSG or the seeded
# Entity cache) - this, not _base_value + _pending_points, is what's actually persisted; a
# pending investment isn't real until the server accepts it.
var _base_value: int = 0

# Points spent on this row via the "+" button since the last confirm/cancel - buffered locally,
# only sent to the server (InvestStatusPointCMSG) when the owning StatusPoints list's Confirm
# button is pressed. Cleared on Cancel, and implicitly cleared for good once a fresh
# StatusValuesComponentSMSG confirms the investment (see set_base_value).
var _pending_points: int = 0

# Whether the master still has status points left to spend, broadcast in from
# StatusPoints._update_row_buttons - a row has no standing connection to that count otherwise.
var _can_spend_globally: bool = false


func _ready() -> void:
	_attribute_label.text = StatusAttribute.short_code(attribute)
	_refresh_display()


## Sets the server-confirmed base value, clearing any pending investment - a fresh push always
## supersedes a stale local buffer (either it reflects the investment now, or Confirm never
## reached the server and re-seeding from scratch is simplest).
func set_base_value(value: int) -> void:
	_base_value = value
	_pending_points = 0
	_refresh_display()


func has_pending_investment() -> bool:
	return _pending_points > 0


## Returns the {attribute, amount} this row wants to invest, for StatusPoints to batch into a
## single InvestStatusPointCMSG on Confirm. Empty if nothing is buffered.
func get_pending_investment() -> Dictionary:
	if _pending_points <= 0:
		return {}
	return {"attribute": attribute, "amount": _pending_points}


## Called by StatusPoints (the owning list) when Cancel is pressed, discarding any buffered,
## not-yet-confirmed investment on this row.
func reset_pending_investment() -> void:
	if _pending_points == 0:
		return
	_pending_points = 0
	_refresh_display()


## Called by StatusPoints (the owning list) whenever the master's spendable status point count
## changes, since a row has no standing connection to that state otherwise.
func set_can_spend_points(can_spend: bool) -> void:
	_can_spend_globally = can_spend
	_refresh_display()


## Buffers a point spend locally - nothing is sent to the server until StatusPoints' Confirm
## button is pressed and batches every row's pending investment into one InvestStatusPointCMSG.
func _on_spend_point_button_pressed() -> void:
	if not _can_spend_globally:
		return
	_pending_points += 1
	_refresh_display()
	investment_changed.emit(self)


func _refresh_display() -> void:
	if _pending_points > 0:
		_value_label.text = "%s (+%s)" % [_base_value, _pending_points]
	else:
		_value_label.text = "%s" % _base_value

	_spend_point_button.visible = _can_spend_globally
