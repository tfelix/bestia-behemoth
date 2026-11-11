package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.OrderBookSmsgProto.OrderBookSMSG.Place
import net.bestia.bnet.proto.OrderBookSmsgProto.OrderBookSMSG.Preset
import net.bestia.bnet.proto.OrderBookSmsgProto
import net.bestia.zone.account.persistence.OrderPreset
import net.bestia.zone.ai.domain.bestia.NamedPlace
import net.bestia.zone.message.SMSG

/** A master's order presets, each with how many bestias follow it, and their named places. */
data class OrderBookSMSG(
  val presets: List<Pair<OrderPreset, Int>>,
  val places: List<NamedPlace>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val payload = OrderBookSmsgProto.OrderBookSMSG.newBuilder()
      .addAllPresets(presets.map { (preset, followers) -> presetOf(preset, followers) })
      .addAllPlaces(places.map(::placeOf))

    return EnvelopeProto.Envelope.newBuilder()
      .setOrderBook(payload)
      .build()
  }

  private fun presetOf(preset: OrderPreset, followers: Int) = Preset.newBuilder()
    .setId(preset.id)
    .setName(preset.name)
    .setOrders(OrdersWire.sheetOf(preset.orders))
    .setFollowers(followers)
    .build()

  private fun placeOf(place: NamedPlace) = Place.newBuilder()
    .setId(place.id)
    .setName(place.name)
    .setKind(Place.Kind.valueOf(place.kind.name))
    .setX(place.center.x)
    .setY(place.center.y)
    .setRadius(place.radius.toInt())
    .build()
}
