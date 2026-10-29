class_name EntityFxDef extends Resource
## One entity effect: how the fx knobs of the entity uber material move while it plays. [EntityFx] plays it.
##
## Each curve runs over the whole effect, x from 0 to 1. A knob without a curve is left to other effects.

enum ParticlesAt { NONE, START, END }

## The server's EntityFxId this def answers ("WING_VANISH"), or empty for an effect only the client plays.
@export var kind := ""
## A status effect id this look stays on for as long as the effect lasts, or 0. Fades in over [member duration].
@export var status_effect_id := 0
## Seconds. A server-driven effect is stretched to the duration the server sends instead.
@export var duration := 0.5
## Plays the curves from 1 back to 0, so one def can serve as both a vanish and an appear.
@export var reverse := false
## Holds the body's animation still while the effect plays.
@export var freeze := false
## Keeps the end state once finished, until the next effect plays: a vanished body stays gone until it appears.
@export var hold_end := false

@export_group("Knobs")
@export var silhouette: Curve
@export var glow: Curve
@export var dissolve: Curve
@export var rim: Curve
@export var flash: Curve
@export var tint_strength: Curve

@export_group("Colours")
@export var glow_colour := Color(0.7, 0.85, 1.0)
@export var rim_colour := Color.WHITE
@export var tint_colour := Color.WHITE
## The way the dissolve edge travels across the body.
@export var dissolve_direction := Vector3.UP

@export_group("Particles")
## A scene whose root is an [EntityFxParticles], spawned at the body's centre.
@export var particles: PackedScene
@export var particles_at := ParticlesAt.NONE
