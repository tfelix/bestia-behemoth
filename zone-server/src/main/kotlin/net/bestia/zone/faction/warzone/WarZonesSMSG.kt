package net.bestia.zone.faction.warzone

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.WarZonesSMSGProto
import net.bestia.zone.message.SMSG

/** Every open war zone as a circle on the map, in metres. */
data class WarZonesSMSG(
  val zones: List<Circle>,
) : SMSG {

  data class Circle(val x: Double, val y: Double, val radius: Double)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = WarZonesSMSGProto.WarZonesSMSG.newBuilder()
    for (zone in zones) {
      message.addZones(WarZonesSMSGProto.WarZone.newBuilder().setX(zone.x).setY(zone.y).setRadius(zone.radius))
    }

    return EnvelopeProto.Envelope.newBuilder()
      .setWarZones(message)
      .build()
  }

  companion object {
    fun of(zones: List<WarZones.WarZone>, voxelSize: Double): WarZonesSMSG {
      return WarZonesSMSG(zones.map { Circle(it.centre.x * voxelSize, it.centre.y * voxelSize, it.radius * voxelSize) })
    }
  }
}
