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

## What to do at something that offers several actions, such as "post" at a blackboard. Absent means open it.
const ACTION := "action"

## Which investment track of a Stronghold, by the server's StrongholdTrack name.
const TRACK := "track"

## A Stronghold watcher's kind, by the server's WatcherKind name.
const WATCHER := "watcher"

const QUEST_ID := "questId"
const ITEM_ID := "itemId"
const AMOUNT := "amount"
const COINS_EACH := "coinsEach"
const DAYS := "days"
