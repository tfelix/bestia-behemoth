extends Resource
class_name EmoteResource

## One emote: a pose the player asks for with [code]/cheer[/code] or from the emote bar, and the clips each body
## plays for it. The server only knows the kind; which clips it means, and whether a body can play it at all, is
## decided here.

## The server's AnimationKind this emote is. Its lowercase is the chat command, so CHEER is typed as /cheer.
@export_enum("CHEER", "JUMP", "POINT", "FLOURISH", "SIT", "LIE") var kind: String = "CHEER"
## Translation key of the name shown in the emote window and on a slot's tooltip.
@export var label_key: String
## Shown on a shortcut slot and in the emote window. Until there is art, [member glyph] stands in.
@export var icon: Texture2D
## A character drawn in place of [member icon] while that is empty.
@export var glyph: String
## Lasts until the entity walks, casts, is hit or dies, rather than playing once.
@export var lasts: bool = false
## What a master plays. Empty when a master cannot do this emote.
@export var master_clips: EmoteClips
## What a bestia plays. Empty when a bestia cannot do this emote.
@export var bestia_clips: EmoteClips


func command() -> String:
	return kind.to_lower()


## The clips [param visual] would play, or null when that body cannot do this emote.
func clips_for(visual: Node) -> EmoteClips:
	if visual is MasterVisual:
		return master_clips
	if visual is BestiaVisual:
		return bestia_clips
	return null
