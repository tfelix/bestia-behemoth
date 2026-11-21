package net.bestia.zone.script

/**
 * Every key a client may put in a [ScriptArgs] bag.
 *
 * The client mirrors this in `bestia-client/src/Game/Script/script_arg_keys.gd`. Both sides spelling a key
 * from a constant is what a bag has instead of a typed message: a rename that reaches only one side then
 * shows up as a script refusing to run rather than as a value silently read as null.
 */
object ScriptArgKeys {

  /** Where the thing goes, in whole voxels. */
  const val POSITION = "position"

  /** Which way it faces, in radians. */
  const val YAW = "yaw"

  /** What was clicked, as a live ECS entity id. */
  const val TARGET_ENTITY_ID = "targetEntityId"

  /** What to do at something that offers several actions, such as `post` at a blackboard. Absent means open it. */
  const val ACTION = "action"

  const val QUEST_ID = "questId"
  const val ITEM_ID = "itemId"
  const val AMOUNT = "amount"
  const val COINS_EACH = "coinsEach"
  const val DAYS = "days"

  /** Which investment track of a Stronghold, by `StrongholdTrack` name. */
  const val TRACK = "track"

  /** A Stronghold watcher's kind, by its name, for the Stronghold window's watcher buttons. */
  const val WATCHER = "watcher"
}
