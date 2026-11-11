package net.bestia.zone.master.bestia

import org.springframework.stereotype.Component

/** What the orders window picks from: a master's presets and places. */
@Component
class OrderBook(
  private val presets: OrderPresetService,
  private val places: NamedPlaces,
) {
  fun of(masterId: Long) = OrderBookSMSG(presets.list(masterId), places.of(masterId))
}
