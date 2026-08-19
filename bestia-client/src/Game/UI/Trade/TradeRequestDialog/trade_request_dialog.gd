extends ConfirmationDialog
class_name TradeRequestDialog

## The "somebody wants to trade" prompt. Both answers go back over the same message, so declining and simply
## closing the dialog are the same thing - the server is told either way rather than being left waiting out
## its timeout.

signal answered(trade_id: int, accept: bool)

var _trade_id: int = 0
var _answered: bool = false


func open_for(trade_id: int, master_name: String) -> void:
	_trade_id = trade_id
	_answered = false
	dialog_text = "%s wants to trade with you." % master_name
	ok_button_text = "Trade"
	cancel_button_text = "Decline"
	popup_centered()


func _on_confirmed() -> void:
	_answer(true)


func _on_canceled() -> void:
	_answer(false)


## Closing the window with the X is a decline too: leaving the request to time out would hold both players'
## trades blocked for no reason.
func _on_close_requested() -> void:
	_answer(false)


func _answer(accept: bool) -> void:
	if _answered:
		return

	_answered = true
	answered.emit(_trade_id, accept)
