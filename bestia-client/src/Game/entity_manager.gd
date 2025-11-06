extends Node
## Keeps track of all entities and their updates. Also removes entities. Its
## up to the scenes to register to this manager to fetch information about entities.
##
## TODO Unclear if this should be globally loaded or if it should be moves as a regular
##   node into the Game node.

var EntityScn = preload("res://Game/Entity/Entity.tscn")

# TODO not sure if int is enough here. Sadly we can not seem to be able to use long for
#   entity IDs. Maybe we need to convert into a string.
var _entities: Dictionary[int, Entity] = {}
var _owned_master_id: int = 0
var _owned_master_entity_id: int = 0


func _ready() -> void:
	ConnectionManager.connect("entity_received", _on_entity_message_received)
	ConnectionManager.connect("chat_received", _on_chat_message_received)
	ConnectionManager.connect("self_received", _on_self_message_received)
	# After a load we request all entities and information about ourself.
	ConnectionManager.get_all_entities()
	ConnectionManager.get_self()

## Checks if we have proper controll attached to the entity we currently control.
func _check_player_controllable_entity() -> void:
	# In the future this must not only checked for master entity id but rather
	# the entity we currently have selected.
	var hasOwnedEntity = _entities.has(_owned_master_entity_id)
	
	if not hasOwnedEntity:
		return
	
	var entity = _entities[_owned_master_entity_id]
	entity.select_for_active()


func _get_or_create_entity(entity_id: int) -> Entity:
	if not _entities.has(entity_id):
		var new_entity = EntityScn.instantiate() as Entity
		_entities[entity_id] = new_entity
		new_entity.entity_id = entity_id
		add_child(new_entity)
		_check_player_controllable_entity()
	
	return _entities[entity_id]


# Returns information who currently is the controllable player and which bestia we control.
# This is important for book keeping.
func _on_self_message_received(msg: SelfSMSG) -> void:
	_owned_master_entity_id = msg.MasterEntityId
	_owned_master_id = msg.MasterId
	_check_player_controllable_entity()
	# for now we dont process the owned bestia but this probably also makes
	# most sense to have a sperate component for this which tracks it.


func _on_entity_message_received(msg: EntitySMSG) -> void:
	var entity = _get_or_create_entity(msg.EntityId)
	
	if msg is PositionComponent:
		entity.update_position(msg)
	elif msg is BestiaVisualComponent:
		entity.update_bestia_visual(msg)
	elif msg is MasterVisualComponentSMSG:
		entity.update_master_visual(msg)
	elif msg is PathComponentSMSG:
		entity.update_path(msg)
	elif msg is SpeedComponentSMSG:
		entity.update_speed(msg)
	elif msg is VanishEntitySMSG:
		entity.vanish(msg)
		_entities.erase(msg.EntityId)
	elif msg is LevelComponentSMSG:
		# no handling so far
		pass
	elif msg is ExpComponentSMSG:
		# no handling so far
		pass
	elif msg is HealthComponentSMSG:
		entity.update_health(msg)
	elif msg is DamageEntitySMSG:
		entity.show_damage(msg)
	else:
		printerr("EntityManager: An EntitySMSG type %s for entity %s was not handled" % [typeof(msg), msg.EntityId])
	# Server sends vanish information -> remove the node + potentially buffered stuff
	# Server sends damage -> lookup entity node and attach damage scn to entity node
	# server sends chat -> lookup entity node and attach chat scn to entity


func _on_chat_message_received(msg: ChatSMSG) -> void:
	if msg.IsPublic() && msg.SenderEntityId != 0:
		var entity = _get_or_create_entity(msg.SenderEntityId)
		entity.show_chat(msg)


func resync_entities() -> void:
	_entities.clear()
	for child in get_children():
		child.queue_free()
	ConnectionManager.get_all_entities()
