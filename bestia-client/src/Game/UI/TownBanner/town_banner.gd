extends Control
## The name of the settlement the player just walked into, large across the upper screen for a few seconds.
##
## Reads the master's PlaceComponentSMSG, as location_panel.gd does. A step into a settlement, or from one into
## another, shows the banner. The first place after login only says where the player already is, and a town that
## grows around them keeps its name, so neither shows it.

const _FADE_IN_SECONDS := 0.6
const _HOLD_SECONDS := 2.6
const _FADE_OUT_SECONDS := 1.2

@onready var _title: Label = %Title
@onready var _subtitle: Label = %Subtitle

var _master_entity_id: int = 0

## The settlement the player stands in, "" outside any. Null until the first place after login is known.
var _settlement = null

var _tween: Tween = null


func _ready() -> void:
	modulate.a = 0.0
	visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId
	_settlement = null

	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	if entity != null and entity.get_place() != null:
		_settlement = _settlement_of(entity.get_place())


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg.EntityId != _master_entity_id or not msg is PlaceComponentSMSG:
		return

	var settlement := _settlement_of(msg)
	var is_first_place: bool = _settlement == null
	var is_new_settlement: bool = settlement != _settlement
	_settlement = settlement

	if not is_first_place and is_new_settlement and settlement != "":
		announce(settlement)


func announce(settlement_name: String) -> void:
	_title.text = settlement_name
	_subtitle.text = tr("TOWN_BANNER_ENTERING")

	if _tween != null:
		_tween.kill()
	visible = true
	modulate.a = 0.0

	_tween = create_tween()
	_tween.tween_property(self, "modulate:a", 1.0, _FADE_IN_SECONDS)
	_tween.tween_interval(_HOLD_SECONDS)
	_tween.tween_property(self, "modulate:a", 0.0, _FADE_OUT_SECONDS)
	_tween.tween_callback(func() -> void: visible = false)


func _settlement_of(place: PlaceComponentSMSG) -> String:
	return place.Name if place.InSettlement else ""
