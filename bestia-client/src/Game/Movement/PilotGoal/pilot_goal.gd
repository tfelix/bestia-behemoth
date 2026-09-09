@abstract
class_name PilotGoal
extends RefCounted


## Where [MovementPilot] is steering, and what happens when it arrives.
##
## The pilot holds one at a time, so setting a goal is how the one before it is cancelled - which is
## what keeps two of them from steering the player at once.


## The destination in tile x/z, the plane the pilot reasons in.
@abstract func destination() -> Vector2


## How near counts as arrived, in tiles. At least sqrt(2), which MovementPilot asserts and relies on.
@abstract func arrival_tiles() -> float


## False once this can no longer be reached at all, whatever the player does.
func is_alive() -> bool:
	return true


## Runs once, on arrival.
func on_arrival() -> void:
	pass
