package net.bestia.zone.crafting.discovery

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.MaterialAxesSmsgProto
import net.bestia.zone.message.SMSG

/** Every crafting material's place in the feature space, by item id. */
data class MaterialAxesSMSG(val materials: Map<Long, CraftVector>) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = MaterialAxesSmsgProto.MaterialAxesSMSG.newBuilder()
      .addAllMaterials(materials.map { (itemId, vector) ->
        MaterialAxesSmsgProto.MaterialAxes.newBuilder()
          .setItemId(itemId.toInt())
          .addAllAxes(vector.toAxisList())
          .build()
      })
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setMaterialAxes(message)
      .build()
  }
}
