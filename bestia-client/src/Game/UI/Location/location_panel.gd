extends PanelContainer
## Where the player is, in words. Top centre, directly under the weather strip.
##
## Reads PlaceComponentSMSG, which the server pushes on spawn and then only when the answer changes -
## so this updates on a border crossing and never in between, and there is no per-tick traffic behind it.
##
## One name, because the server decides which place the player is in. Nothing here ranks a town against
## the region around it.
##
## Starts hidden and stays hidden against a server that sends no place, which is how an older
## zone-server presents itself. A panel reading "Unknown" is worse than no panel: it says the world has
## nowhere in it rather than that the client is talking to something older than the feature.

@onready var _name: Label = $Margin/Name

## Which entity's place this panel shows. Zero until SelfSMSG says.
var _master_entity_id: int = 0


func _ready() -> void:
	visible = false
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId
	_seed_from_cache()


## Seeds from the master Entity's cache (kept up to date by entity_manager.gd/entity.gd), because
## _on_entity_received below has to drop everything arriving before _master_entity_id is known - and the
## place is pushed once at spawn, so without this the panel stays empty until the player happens to walk
## into a different region. Same problem clock.gd solves with PublishNow, and the same shape as
## MasterProfile._seed_from_cache.
func _seed_from_cache() -> void:
	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_master_entity_id) if entity_manager else null
	if entity == null:
		return

	var place: PlaceComponentSMSG = entity.get_place()
	if place != null:
		_apply(place)


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg.EntityId != _master_entity_id:
		return
	if msg is PlaceComponentSMSG:
		_apply(msg)


func _apply(msg: PlaceComponentSMSG) -> void:
	if msg.Name.is_empty():
		return

	visible = true
	_name.text = msg.Name
