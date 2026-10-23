package net.bestia.zone.control.net

import net.bestia.bnet.proto.MoveEntityProto
import net.bestia.zone.geometry.DeltaPaths
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class MoveEntityCMSG(
  override val playerId: Long,
  val entityId: EntityId,
  /** Empty stops the entity. */
  val waypoints: List<Vec3L>,
  /** Adds [waypoints] to the journey under way instead of replacing it. */
  val append: Boolean = false,
  /** The steps the client already walks towards the first waypoint; see [net.bestia.zone.control.ClientLeg]. */
  val firstLeg: List<Vec3L> = emptyList(),
  /** What the journey is called on the map; the saved route's name when it follows one. */
  val name: String = "",
  val savedRouteId: Long? = null,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, move: MoveEntityProto.MoveEntity): MoveEntityCMSG {
      return MoveEntityCMSG(
        playerId = accountId,
        entityId = move.entityId,
        waypoints = move.waypointsList.map { Vec3L(it.x, it.y, it.z) },
        append = move.append,
        firstLeg = if (move.hasFirstLeg()) DeltaPaths.decode(move.firstLeg) else emptyList(),
        name = move.name,
        savedRouteId = move.savedRouteId.takeIf { it != 0L },
      )
    }
  }
}
