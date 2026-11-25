package net.bestia.zone.trade

import net.bestia.bnet.proto.TradeStateSmsgProto
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.BestiaOrigin
import net.bestia.zone.bestia.IvReading
import net.bestia.zone.bestia.Sex
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId

/**
 * A party bestia in one side of a trade, as it stood when it was offered. It stays in the world, and its owner's,
 * until the trade goes through; [bag] is what both sides were shown it carries, and the trade fails if that is
 * no longer so when it settles.
 */
data class OfferedBestia(
  val playerBestiaId: PlayerBestiaId,
  val entityId: EntityId,
  val mobId: Int,
  val name: String?,
  val level: Int,
  val sex: Sex?,
  val origin: BestiaOrigin,
  val originMasterName: String,
  val ivs: IndividualValues,
  val bag: CarriedItems,
) {

  /** What a bestia carries and wears, by instance or pile, so a change in either is seen. */
  data class CarriedItems(
    val lines: Set<String>,
    val wearsGear: Boolean,
  ) {
    val count: Int get() = lines.size

    val isEmpty: Boolean get() = lines.isEmpty() && !wearsGear
  }

  fun toProto(): TradeStateSmsgProto.TradeOfferBestia {
    val proto = TradeStateSmsgProto.TradeOfferBestia.newBuilder()
      .setPlayerBestiaId(playerBestiaId)
      .setEntityId(entityId)
      .setMobId(mobId)
      .setName(name.orEmpty())
      .setLevel(level)
      .setOrigin(origin.toBnet())
      .setOriginMasterName(originMasterName)
      // Grades only, for either side: exact numbers are Bloodline Study's, and only for the bestia's owner.
      .setIvs(IvReading(ivs, exact = false).toBnet())
      .setCarriedItemCount(bag.count)
      .setWearsGear(bag.wearsGear)
    sex?.let { proto.setSex(it.toBnet()) }

    return proto.build()
  }
}
