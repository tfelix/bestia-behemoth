package net.bestia.zone.item.stat

import net.bestia.bnet.proto.ItemStatValueProto

/** One stat on one item, such as +3 STR. */
data class StatValue(
  val stat: ItemStat,
  val value: Int
) {

  fun toProto(): ItemStatValueProto.ItemStatValue {
    return ItemStatValueProto.ItemStatValue.newBuilder()
      .setStat(stat.name)
      .setValue(value)
      .build()
  }
}
