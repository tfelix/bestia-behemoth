extends Area3D
class_name PropPicker

## The click target of one collectible static prop. Built per prop by StaticEntityRenderer.
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


func _on_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	MouseManager.object_clicked(self, event, event_position)
