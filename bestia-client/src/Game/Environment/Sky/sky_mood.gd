class_name SkyMood
extends Resource

## An artistic grade over the whole sky and its light, such as the red of a world that is ending.
##
## Each tint recolours its part while keeping that part's brightness, so the grade works at every hour: a
## doomsday noon is a bright red, a doomsday midnight a dark one. environment.gd blends it in by its
## mood_strength.

@export var sky_tint: Color = Color(0.85, 0.18, 0.10)
@export var horizon_tint: Color = Color(0.95, 0.30, 0.12)
@export var cloud_tint: Color = Color(0.80, 0.22, 0.14)
@export var light_tint: Color = Color(1.0, 0.45, 0.30)
@export var fog_tint: Color = Color(0.75, 0.22, 0.12)

## Scales the brightness of everything the mood touches. Below 1 for ash or smoke that chokes the light.
@export_range(0, 2, 0.01) var brightness: float = 1.0


## A colour recoloured toward a tint at the same brightness, blended in by strength.
func grade(colour: Color, tint: Color, strength: float) -> Color:
	var recoloured := tint * (colour.get_luminance() / maxf(tint.get_luminance(), 0.001))

	# A saturated tint has little luminance of its own, so matching a bright sky can push a channel far past 1.
	# Clipped, a deep red goes salmon; scaled back down, it stays red and is simply as bright as red can be.
	var peak := maxf(recoloured.r, maxf(recoloured.g, recoloured.b))
	if peak > 1.0:
		recoloured /= peak

	recoloured *= brightness
	recoloured.a = colour.a

	return colour.lerp(recoloured, strength)
