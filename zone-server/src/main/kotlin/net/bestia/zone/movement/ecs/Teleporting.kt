package net.bestia.zone.movement.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * A body on its way out: it stands still while it vanishes, then `TeleportSystem` puts it down at [destination].
 *
 * Server-only. Movement and casting are refused while it is on, so the body does not walk off mid-vanish.
 */
class Teleporting(
  val destination: Destination,
  /** Seconds the vanish still plays before the body is moved. */
  var secondsUntilMove: Float,
) : Component {

  sealed interface Destination {
    /** A random spot within [radiusMetres] of where the body stands, on ground the player already sees. */
    data class Nearby(val radiusMetres: Long) : Destination

    /** The save point, looked up off the tick and posted back; null until the lookup has answered. */
    class Home : Destination {
      var savePoint: Vec3L? = null
      var lookupFailed = false
    }
  }
}
