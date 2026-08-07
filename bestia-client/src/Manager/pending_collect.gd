extends RefCounted
class_name PendingCollect

## A prop the player clicked from too far away, remembered while they walk to it.
##
## Held by MouseManager rather than by MouseStateDefault, and that is not a detail: enter_default()
## constructs a [i]fresh[/i] MouseStateDefault every time, so a player who opens skill targeting and
## escapes back out would silently lose a pending walk stored on the state.


## The click target we are walking to. Its validity is the liveness test: the node is freed when the
## chunk unloads, when the manifest resets, and when the server says the prop is gone - so one
## is_instance_valid() check covers all three without any signal plumbing.
var picker: PropPicker = null

## Where it stands, cached so a freed picker can still be logged sensibly.
var target: Vector3 = Vector3.ZERO

## Wall-clock ms after which we give up. PathCalculator ignores terrain and entity collision, so a walk
## can simply never arrive; without this the pending would sit forever.
var deadline_msec: int = 0

## Frames spent stopped and still out of reach. The walk ending short is the ordinary failure, and one
## frame of it is not enough to be sure - the server can replace the path mid-step.
var stalled_frames: int = 0
