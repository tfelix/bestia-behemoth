class_name SkillCooldowns
extends RefCounted

## When each skill may be used again, as the server last said. Kept in local milliseconds because the server
## sends a duration: the two clocks differ.

static var _ready_at_msec: Dictionary[int, int] = {}


static func start(skill_id: int, remaining_ms: int) -> void:
	_ready_at_msec[skill_id] = Time.get_ticks_msec() + remaining_ms


static func remaining_seconds(skill_id: int) -> float:
	var ready: int = _ready_at_msec.get(skill_id, 0)
	return maxf(ready - Time.get_ticks_msec(), 0) / 1000.0
