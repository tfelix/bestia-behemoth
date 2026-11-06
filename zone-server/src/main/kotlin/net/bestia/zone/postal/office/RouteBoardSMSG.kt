package net.bestia.zone.postal.office

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.RouteBoardSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.courier.CourierService
import net.bestia.zone.util.EntityId

data class RouteBoardSMSG(
  val postmasterEntityId: EntityId,
  val parcels: List<CourierService.BoardLine>,
  /** What the courier just delivered here, on the board shown right after. */
  val delivered: CourierService.Delivery? = null,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setRouteBoard(
        RouteBoardSmsgProto.RouteBoardSMSG.newBuilder()
          .setPostmasterEntityId(postmasterEntityId)
          .addAllParcels(parcels.map { line ->
            RouteBoardSmsgProto.RouteBoardParcel.newBuilder()
              .setParcelId(line.parcelId)
              .setDestinationName(line.destinationName)
              .setWeight(line.weightUnits)
              .setPay(line.pay)
              .setLatestArrivalEpochSeconds(line.latestArrival.epochSecond)
              .build()
          })
          .setDeliveredParcels(delivered?.parcels ?: 0)
          .setDeliveredPay(delivered?.pay ?: 0)
      )
      .build()
  }
}
