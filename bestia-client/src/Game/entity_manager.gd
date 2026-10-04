extends Node
class_name EntityManager
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
## What the player drives right now: the master, or one of [member _owned_bestias] once the server confirmed it.
var _controlled_entity_id: int = 0
## The master's bestia, as the last SelfSMSG or OwnedBestiasSMSG listed them.
var _owned_bestias: Array[BestiaInfo] = []

## The player took control of another of their entities. [param previous_id] is 0 on the first one.
signal controlled_entity_changed(previous_id: int, entity_id: int)
## The list of bestia the master owns was replaced.
signal owned_bestias_changed(bestias: Array[BestiaInfo])


## EntityManager isn't an autoload (see the TODO above - its lifecycle is tied to the
## Game scene, reset via resync_entities() on zone changes, unlike the true global
## singletons in project.godot), so there's no direct global name for it. This is the
## one place that knows how to find it, so callers don't each duplicate the group lookup.
## Usable from any node's _ready() - see _enter_tree() - but still null before the Game
## scene exists at all, so guard the result.
static func get_instance() -> EntityManager:
	var loop := Engine.get_main_loop() as SceneTree
	return loop.get_first_node_in_group("entity_manager") as EntityManager


## Joins the group here rather than in _ready(), because _ready() is too late for half the
## scene to find us. Godot propagates _enter_tree() across the whole subtree before it runs
## a single _ready(), whereas _ready() runs in tree order - so a sibling declared ahead of
## us in Game.tscn is fully ready while our own _ready() has yet to fire. UI is exactly such
## a sibling: when the group was joined below, it asked get_instance() for the entity manager
## its map views follow, got null, and cached it. Both views then sat on the world origin and
## drew nothing but fog for the rest of the session, with no player marker and no error.
func _enter_tree() -> void:
	add_to_group("entity_manager")


func _ready() -> void:
	ConnectionManager.connect("entity_received", _on_entity_message_received)
	ConnectionManager.connect("chat_received", _on_chat_message_received)
	ConnectionManager.connect("self_received", _on_self_message_received)
	ConnectionManager.connect("owned_bestias_received", _on_owned_bestias_received)
	ConnectionManager.connect("active_entity_received", _on_active_entity_received)
	# Information about ourself; the entities around us arrive with the ground they stand on.
	ConnectionManager.get_self()


## The Entity node the player currently controls - their master or one of their bestia - or null before the
## initial self/entity sync has arrived.
func get_controlled_entity() -> Entity:
	return _entities.get(_controlled_entity_id)


func get_controlled_entity_id() -> int:
	return _controlled_entity_id


## The player's master, whichever entity they are driving.
func get_master_entity() -> Entity:
	return _entities.get(_owned_master_entity_id)


func get_owned_bestias() -> Array[BestiaInfo]:
	return _owned_bestias


## True for the master and for every bestia it owns.
func is_owned_entity_id(entity_id: int) -> bool:
	if entity_id == 0:
		return false
	if entity_id == _owned_master_entity_id:
		return true
	for bestia in _owned_bestias:
		if bestia.EntityId == entity_id:
			return true
	return false


## The entity id of the player's own master, or 0 before the initial self sync. Kept accessible so
## the logout flow can recognise its own master's vanish (= logout complete) even after the node is
## already gone from the entity table.
func get_owned_master_entity_id() -> int:
	return _owned_master_entity_id


## Returns the Entity node for entity_id, or null if it is not currently known
## (out of range / not yet synced).
func get_entity(entity_id: int) -> Entity:
	return _entities.get(entity_id)


## Client-side friend/enemy heuristic: the local player's own entities, and anything of a species
## nothing may damage - a townsperson is somebody to talk to, not a target to snap onto.
## TODO(party/guild): once bestias carry a party/guild flag, fold that check in here (e.g. matching
## party/guild id against the local player's).
## This is the ONLY place disposition should be decided - no other code should inline
## its own friend/enemy check.
func is_entity_friendly(entity: Entity) -> bool:
	return is_owned_entity_id(entity.entity_id) or entity.is_non_combatant()


## DEPRECATED We need to come up with a better solution this can not work and scale. We need certain
##    mouse over fields that send their position so this then decides what is the closes, best entity.
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


## Attaches the camera and input to the entity we control, once it exists here.
func _check_player_controllable_entity() -> void:
	var entity: Entity = _entities.get(_controlled_entity_id)
	if entity != null:
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
	# A fresh self message means a fresh session, which the server always starts on the master.
	_set_controlled_entity(msg.MasterEntityId)
	_replace_owned_bestias(msg.AvailableBestias)


func _on_owned_bestias_received(msg: OwnedBestiasSMSG) -> void:
	_replace_owned_bestias(msg.Bestias)


func _on_active_entity_received(msg: ActiveEntitySMSG) -> void:
	_set_controlled_entity(msg.EntityId)


func _replace_owned_bestias(bestias: Array[BestiaInfo]) -> void:
	_owned_bestias = bestias.duplicate()
	owned_bestias_changed.emit(_owned_bestias)


func _set_controlled_entity(entity_id: int) -> void:
	var previous_id := _controlled_entity_id
	if previous_id == entity_id:
		_check_player_controllable_entity()
		return

	var previous: Entity = _entities.get(previous_id)
	if previous != null:
		previous.remove_as_active()

	_controlled_entity_id = entity_id
	_check_player_controllable_entity()
	controlled_entity_changed.emit(previous_id, entity_id)


func _on_entity_message_received(msg: EntitySMSG) -> void:
	var entity = _get_or_create_entity(msg.EntityId)
	
	if msg is PositionComponent:
		entity.update_position(msg)
	elif msg is VisualComponentSMSG:
		entity.update_visual(msg)
	elif msg is MasterVisualComponentSMSG:
		entity.update_master_visual(msg)
	elif msg is TownsfolkVisualComponentSMSG:
		entity.update_townsfolk_visual(msg)
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
	elif msg is DeadComponentSMSG:
		entity.update_dead(msg)
	elif msg is HealthComponentSMSG:
		entity.update_health(msg)
	elif msg is ManaComponentSMSG:
		# Cached on the entity rather than only handled by MasterProfile (which listens to
		# ConnectionManager.entity_received itself): the HUD may not exist yet when this arrives,
		# and it seeds itself from the cache once it learns its master entity id.
		entity.update_mana(msg)
	elif msg is StaminaComponentSMSG:
		entity.update_stamina(msg)
	elif msg is PlaceComponentSMSG:
		# Owner-only on the wire, so this only ever arrives for the local master. Cached on the entity for
		# the reason update_mana states: the location panel may not exist yet when the first one lands.
		entity.update_place(msg)
	elif msg is AreaNameComponentSMSG:
		# No handling yet. A label over a town gate needs a visual that can carry one, which
		# StaticEntityRenderer deliberately does not - see its note on nameplates.
		pass
	elif msg is CarryCapacityComponentSMSG:
		# no handling so far. The local player's carry capacity is handled directly by
		# MasterProfile, same as InventoryComponentSMSG below.
		pass
	elif msg is CastingComponentSMSG:
		# A completed and an interrupted cast are indistinguishable on the wire and both just
		# end the bar - see Removed on CastingComponentSMSG.
		if msg.Removed:
			entity.clear_casting()
		else:
			entity.update_casting(msg)
	elif msg is ConstructionComponentSMSG:
		# No branch on Removed, unlike the cast above: a site that finishes or is destroyed is destroyed as an
		# entity too, so VanishEntitySMSG is what ends it.
		entity.update_construction(msg)
	elif msg is BuffListSMSG:
		entity.update_effects(msg)
	elif msg is DamageEntitySMSG:
		entity.show_damage(msg)
	elif msg is InventoryComponentSMSG:
		# no handling so far. The inventory of our entity is handled via a own handler
		# directly in the inventory node. On an per entity level it is not handled.
		pass
	elif msg is SkillListSMSG:
		# no handling so far. The skill list is handled via a own handler
		# directly in the skills node. On an per entity level it is not handled.
		pass
	elif msg is SkillPointsComponentSMSG:
		entity.update_skill_points(msg)
	elif msg is EquipmentComponentSMSG:
		entity.update_equipment(msg)
	elif msg is StatusValuesComponentSMSG:
		entity.update_status_values(msg)
	elif msg is BaseStatusValuesComponentSMSG:
		entity.update_base_status_values(msg)
	elif msg is StatusPointsComponentSMSG:
		entity.update_status_points(msg)
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
