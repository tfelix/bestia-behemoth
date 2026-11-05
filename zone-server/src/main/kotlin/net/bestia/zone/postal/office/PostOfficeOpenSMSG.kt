package net.bestia.zone.postal.office

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PostOfficeOpenSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class PostOfficeOpenSMSG(
  val postmasterEntityId: EntityId,
  val officeName: String,
  val level: Int,
  val homeOfficeName: String,
  val isHome: Boolean,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setPostOfficeOpen(
        PostOfficeOpenSmsgProto.PostOfficeOpenSMSG.newBuilder()
          .setPostmasterEntityId(postmasterEntityId)
          .setOfficeName(officeName)
          .setLevel(level)
          .setHomeOfficeName(homeOfficeName)
          .setIsHome(isHome)
      )
      .build()
  }
}
