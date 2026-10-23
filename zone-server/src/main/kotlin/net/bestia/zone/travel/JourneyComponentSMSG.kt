package net.bestia.zone.travel

import net.bestia.bnet.proto.JourneyComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

data class JourneyComponentSMSG(
  override val entityId: Long,
  val waypoints: List<Vec3L>,
  val nextIndex: Int,
  val name: String,
  val route: List<Vec3L>,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val journey = JourneyComponentSMSGProto.JourneyComponentSMSG.newBuilder()
      .addAllWaypoints(waypoints.map { it.toProto() })
      .setNextIndex(nextIndex)
      .setName(name)
      .addAllRoute(route.map { it.toProto() })
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setJourney(journey))
  }

  private companion object {
    fun Vec3L.toProto(): Vec3OuterClass.Vec3 {
      return Vec3OuterClass.Vec3.newBuilder().setX(x).setY(y).setZ(z).build()
    }
  }
}
