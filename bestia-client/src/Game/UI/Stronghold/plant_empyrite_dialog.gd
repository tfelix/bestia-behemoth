extends ConfirmationDialog
class_name PlantEmpyriteDialog

## Asks whether to plant the Empyrite at the seed the player clicked. Confirming clicks the sprout again with the
## "plant" action; the server checks everything once more, since the guild or the purse may have changed meanwhile.

## Mirrors net.bestia.zone.stronghold.EmpyriteSeedInteraction.PLANT.
const _PLANT := "plant"

var _sprout_entity_id: int = 0


func _ready() -> void:
	title = tr("STRONGHOLD_PLANT_TITLE")
	ok_button_text = tr("STRONGHOLD_PLANT")
	dialog_autowrap = true
	confirmed.connect(_on_confirmed)
	ConnectionManager.empyrite_plant_offered.connect(_on_offered)


func _on_offered(message: EmpyritePlantOfferSMSG) -> void:
	_sprout_entity_id = message.SproutEntityId
	dialog_text = tr("STRONGHOLD_PLANT_TEXT") % [message.SeedName, message.SiteMinutes, message.Coins]
	popup_centered(Vector2i(340, 0))


func _on_confirmed() -> void:
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, _PLANT)
	ConnectionManager.interact_entity(_sprout_entity_id, args)
