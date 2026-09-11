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
}
