package net.bestia.zone.postal.office

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ParcelsSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.util.EntityId

data class ParcelsSMSG(
  val postmasterEntityId: EntityId,
  val parcels: List<ParcelService.ParcelView>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setParcels(
        ParcelsSmsgProto.ParcelsSMSG.newBuilder()
          .setPostmasterEntityId(postmasterEntityId)
          .addAllParcels(parcels.map { it.toProto() })
      )
      .build()
  }
}
