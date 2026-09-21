extends Area3D
class_name PropPicker

## The click target of one static prop - one a click takes, or one a click interacts with. Built per prop
## by StaticEntityRenderer.
##
## [b]An Area3D, and that is the whole safety argument.[/b] MouseManager.get_floor_hit_at_mouse - the
## client's only raycast - builds its query with PhysicsRayQueryParameters3D.create(), which leaves
## [code]collide_with_areas[/code] at [code]false[/code]. So this is invisible to it, and the ground
## cursor plus every skill and item targeting indicator keeps tracking the terrain [i]through[/i] a
## crystal instead of snapping off it. Godot's viewport picking, by contrast, does deliver
## [code]input_event[/code] to an Area3D, so a click still lands here.
##
## A StaticBody3D would have done neither favour: TerrainRenderer's own note explains that the floor ray
## hits any body and then discards it unless it is in the "floor" group, so a crystal field would have put
## a hole in every indicator that crossed it.
##
## [b]entity_id is ephemeral.[/b] It is a live server id read off a ChunkStaticEntitiesSMSG entry and is
## only valid while the chunk is held. Nothing needs to guard that: this node is freed with its chunk's
## container, so a reference that is still valid necessarily holds an id that is still current.


## Live server entity id of the prop this stands on.
var entity_id: int = 0

## StaticEntityKind ordinal, so a caller can tell a crystal from a shard without a second lookup.
var kind: int = 0

## Whether clicking this takes the prop, as opposed to interacting with it. Set from PropAppearance, which
## is where the client decides what a click on each kind means.
var collectible: bool = true

## What a click here is for, as a PropAppearance.PropAction name - see DefaultAction.from_prop_name.
var action: StringName = &"none"


func _on_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_mouse_entered() -> void:
	MouseManager.get_instance().on_object_hover(self, true)


func _on_mouse_exited() -> void:
	MouseManager.get_instance().on_object_hover(self, false)
