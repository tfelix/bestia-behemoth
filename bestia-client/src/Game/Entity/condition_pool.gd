extends RefCounted
class_name ConditionPool

## One current/maximum condition value - HP, mana or stamina. Mirrors the server's
## net.bestia.zone.battle.status.CurMax, which is the shape all three arrive in on the wire as
## HealthComponentSMSG / ManaComponentSMSG / StaminaComponentSMSG.
##
## Typed rather than a bare Dictionary on purpose: these are cached to be read back later by UI that
## was not listening when the value arrived, and a mistyped string key would come back null and paint
## an empty bar instead of raising anything. A missing pool is the null reference, never a
## zero-valued one - "never received" and "actually empty" have to stay distinguishable, since a
## master at 0 HP is dead and one we have heard nothing about is merely not loaded yet.
##
## Named `maximum`, not `max`: `max` is a GDScript global function and shadowing it inside the class
## would be legal but would earn a SHADOWED_GLOBAL_IDENTIFIER warning.

var current: int = 0
var maximum: int = 0


func _init(p_current: int = 0, p_maximum: int = 0) -> void:
	current = p_current
	maximum = p_maximum
