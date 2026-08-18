extends Resource
class_name EffectResource

## One spell or ground effect the server can spawn as an entity, keyed by the id it sends in a
## VisualComponentSMSG with kind EFFECT. Purely presentation: the server decides where the effect is,
## how large it is and how long it lasts, and the scene below only draws it.

@export var effect_id: int

## Instantiated as the entity's visual. Should clean up after itself on being freed - the server
## destroys the entity when the effect expires, which vanishes the whole node.
@export var effect_visual: PackedScene
