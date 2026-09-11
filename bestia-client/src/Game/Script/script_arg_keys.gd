extends Object
class_name ScriptArgKeys

## Every key a client may put in a ScriptArgs bag.
##
## Mirrors net.bestia.zone.script.ScriptArgKeys. Both sides spelling a key from a constant is what a bag
## has instead of a compiler: a rename that reaches only one side shows up as a script refusing to run
## rather than as a value silently read as null.

## Where the thing goes, as a tile coordinate.
const POSITION := "position"

## Which way it faces, in radians.
const YAW := "yaw"

## What was clicked, as a live ECS entity id.
const TARGET_ENTITY_ID := "targetEntityId"
