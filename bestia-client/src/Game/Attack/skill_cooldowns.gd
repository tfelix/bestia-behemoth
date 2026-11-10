class_name SkillCooldowns
extends RefCounted

## When each skill may be used again, as the server last said. Kept in local milliseconds because the server
## sends a duration: the two clocks differ.
##
## Keyed by entity as well as skill, because two bestias of one species know the same skill and recover apart.
## A guild skill recovers for the whole guild, so it is kept under [constant ANY_ENTITY] and counts for whoever
## is played.

const ANY_ENTITY := 0

## The entity the hotbar acts for; set by the hotbar when control switches.
static var controlled_entity_id: int = ANY_ENTITY

static var _ready_at_msec: Dictionary[String, int] = {}


static func start(skill_id: int, remaining_ms: int, entity_id: int = ANY_ENTITY) -> void:
	_ready_at_msec[_key(entity_id, skill_id)] = Time.get_ticks_msec() + remaining_ms


static func remaining_seconds(skill_id: int) -> float:
	var ready: int = maxi(
		_ready_at_msec.get(_key(controlled_entity_id, skill_id), 0),
		_ready_at_msec.get(_key(ANY_ENTITY, skill_id), 0)
	)
	return maxf(ready - Time.get_ticks_msec(), 0) / 1000.0


static func _key(entity_id: int, skill_id: int) -> String:
	return "%d:%d" % [entity_id, skill_id]
