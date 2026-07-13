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
	add_to_group("entity_manager")
	ConnectionManager.connect("entity_received", _on_entity_message_received)
	ConnectionManager.connect("chat_received", _on_chat_message_received)
	ConnectionManager.connect("self_received", _on_self_message_received)
	# After a load we request all entities and information about ourself.
	ConnectionManager.get_all_entities()
	ConnectionManager.get_self()


## Returns the Entity node the player currently controls (their bestia master), or
## null before the initial self/entity sync has arrived.
func get_owned_entity() -> Entity:
	return _entities.get(_owned_master_entity_id)


## Returns the Entity node for entity_id, or null if it is not currently known
## (out of range / not yet synced).
func get_entity(entity_id: int) -> Entity:
	return _entities.get(entity_id)


## Client-side friend/enemy heuristic. Currently: "owned by the local player" = friendly,
## everything else = enemy. TODO(party/guild): once bestias carry a party/guild flag,
## fold that check in here (e.g. matching party/guild id against the local player's).
## This is the ONLY place disposition should be decided - no other code should inline
## its own friend/enemy check.
func is_entity_friendly(entity: Entity) -> bool:
	return entity == get_owned_entity()


## Closest Entity to world_position within max_distance whose disposition matches
## filter ("enemy" or "friendly"), or null if none qualify. Used by
## MouseStateSkillTargeting to snap an entity-target skill onto a nearby valid target.
## Simple O(n) scan over all known entities - matches this codebase's existing style
## (no spatial partitioning exists anywhere) and entity counts are small.
func get_closest_entity(world_position: Vector3, max_distance: float, filter: String) -> Entity:
	var best: Entity = null
	var best_dist_sq: float = max_distance * max_distance
	for value in _entities.values():
		var entity: Entity = value
		var friendly: bool = is_entity_friendly(entity)
		if filter == "enemy" and friendly:
			continue
		if filter == "friendly" and not friendly:
			continue
		var entity_pos: Vector3 = entity.global_position
		var d_sq: float = entity_pos.distance_squared_to(world_position)
		if d_sq <= best_dist_sq:
			best_dist_sq = d_sq
			best = entity
	return best


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
	elif msg is AnimationComponentSMSG:
		entity.update_animation(msg)
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
	elif msg is BuffListSMSG:
		entity.update_buffs(msg)
	elif msg is DamageEntitySMSG:
		entity.show_damage(msg)
	elif msg is ItemVisualComponentSMSG:
		entity.update_item_visual(msg)
	elif msg is InventoryComponentSMSG:
		# no handling so far. The inventory of our entity is handled via a own handler
		# directly in the inventory node. On an per entity level it is not handled.
		pass
	elif msg is SkillListSMSG:
		# no handling so far. The skill list is handled via a own handler
		# directly in the skills node. On an per entity level it is not handled.
		pass
	elif msg is SkillPointsComponentSMSG:
		# no handling so far. Skill points are handled via a own handler
		# directly in the skills node. On an per entity level it is not handled.
		pass
	else:
		printerr("EntityManager: An EntitySMSG type %s for entity %s was not handled" % [msg.GetMessageName(), msg.EntityId])
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
