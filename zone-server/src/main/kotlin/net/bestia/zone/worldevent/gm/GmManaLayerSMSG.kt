package net.bestia.zone.worldevent.gm

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GmManaLayerSmsgProto
import net.bestia.bnet.proto.WeatherSMSGProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.mana.ManaTier

/** See `gm_mana_layer_smsg.proto`. The cells run west to east, then south to north; an empty grid clears the layer. */
data class GmManaLayerSMSG(
  val westMetres: Double,
  val southMetres: Double,
  val cellMetres: Double,
  val width: Int,
  val cells: List<Cell>,
  val provinces: List<Province>,
  val events: List<Event>,
) : SMSG {

  data class Cell(val density: Double, val tier: ManaTier, val province: Int, val corruption: Double = 0.0)

  data class Province(val index: Int, val name: String, val budget: Double, val quietSeconds: Long, val sanctuary: Boolean)

  data class Event(val instanceId: Long, val eventId: String, val eastMetres: Double, val northMetres: Double, val province: Int)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GmManaLayerSmsgProto.GmManaLayerSMSG.newBuilder()
      .setWestMetres(westMetres)
      .setSouthMetres(southMetres)
      .setCellMetres(cellMetres.toFloat())
      .setWidth(width)

    cells.forEach {
      proto.addDensity(it.density.toFloat())
      proto.addTier(WeatherSMSGProto.ManaTier.forNumber(it.tier.ordinal))
      proto.addProvince(it.province)
      proto.addCorruption(it.corruption.toFloat())
    }
    provinces.forEach {
      proto.addProvinces(
        GmManaLayerSmsgProto.GmProvince.newBuilder()
          .setIndex(it.index)
          .setName(it.name)
          .setBudget(it.budget.toFloat())
          .setQuietSeconds(it.quietSeconds.toInt())
          .setSanctuary(it.sanctuary)
      )
    }
    events.forEach {
      proto.addEvents(
        GmManaLayerSmsgProto.GmWorldEvent.newBuilder()
          .setInstanceId(it.instanceId)
          .setEventId(it.eventId)
          .setEastMetres(it.eastMetres)
          .setNorthMetres(it.northMetres)
          .setProvince(it.province)
      )
    }

    return EnvelopeProto.Envelope.newBuilder().setGmManaLayer(proto).build()
  }

  companion object {
    val CLEARED = GmManaLayerSMSG(0.0, 0.0, 0.0, 0, emptyList(), emptyList(), emptyList())
  }
}
