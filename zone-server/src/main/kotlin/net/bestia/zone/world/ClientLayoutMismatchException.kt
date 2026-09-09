package net.bestia.zone.world

import net.bestia.zone.BestiaException

/**
 * Thrown when a world's geometry is not the one clients compile in - see [ClientWorldContract].
 *
 * Its own exception rather than a case of [IncompatibleWorldException] because the remedy is different:
 * that one is a world this build cannot generate, and regenerating is a legitimate answer. This world
 * generates perfectly and would simply be drawn in the wrong place, so no policy applies and regenerating
 * fixes nothing - either the configuration goes back to what clients expect, or a new client ships.
 *
 * Refusing the boot is the deliberately blunt end of a spectrum, because the failure it replaces is silent:
 * a wrong chunk size still meshes terrain correctly, and only the props and colliders land somewhere else.
 * `WorldGenConfig.enforceClientLayout` is the way out for a server that knowingly runs a geometry no client
 * will see, which is every test.
 */
class ClientLayoutMismatchException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_CLIENT_LAYOUT_MISMATCH"
  }
}
