package net.bestia.zone.travel

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SavedRoutesProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.message.SMSG

data class SavedRoutesSMSG(val routes: List<SavedRouteService.View>) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val list = SavedRoutesProto.SavedRoutesSMSG.newBuilder()
    for (route in routes) {
      list.addRoutes(
        SavedRoutesProto.SavedRoute.newBuilder()
          .setId(route.id)
          .setName(route.name)
          .setCustomName(route.customName)
          .addAllWaypoints(route.waypoints.map { Vec3OuterClass.Vec3.newBuilder().setX(it.x).setY(it.y).setZ(it.z).build() })
      )
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setSavedRoutes(list)
      .build()
  }
}
