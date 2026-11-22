package net.bestia.zone.stronghold

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StrongholdTabSmsgProto
import net.bestia.zone.message.SMSG

/** The guild window's Stronghold tab: the guild's Strongholds with their waiting treasure, and its storage. */
data class StrongholdTabSMSG(
  val strongholds: List<Card>,
  val isLeader: Boolean,
  val storage: Storage,
) : SMSG {

  data class Card(
    val rank: Int,
    val name: String,
    val isSite: Boolean,
    val hp: Int,
    val maxHp: Int,
    val defense: Int,
    val storage: Int,
    val economy: Int,
    val hasSteward: Boolean,
    val watchers: Int,
    val watcherPosts: Int,
    val drops: List<TreasureDrop>,
    val maxDrops: Int,
    val nextDropSeconds: Long,
  )

  data class Storage(val learned: Boolean, val used: Int, val slots: Int, val sealed: Boolean, val sendAwayCoins: Long)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = StrongholdTabSmsgProto.StrongholdTabSMSG.newBuilder()
      .setIsLeader(isLeader)
      .setHasGuildStorage(storage.learned)
      .setStorageUsed(storage.used)
      .setStorageSlots(storage.slots)
      .setStorageSealed(storage.sealed)
      .setSendAwayCoins(storage.sendAwayCoins)

    strongholds.forEach { proto.addStrongholds(cardOf(it)) }

    return EnvelopeProto.Envelope.newBuilder().setStrongholdTab(proto).build()
  }

  private fun cardOf(card: Card): StrongholdTabSmsgProto.StrongholdCard {
    return StrongholdTabSmsgProto.StrongholdCard.newBuilder()
      .setSeedRank(card.rank)
      .setName(card.name)
      .setIsSite(card.isSite)
      .setHp(card.hp)
      .setMaxHp(card.maxHp)
      .setDefense(card.defense)
      .setStorage(card.storage)
      .setEconomy(card.economy)
      .setHasSteward(card.hasSteward)
      .setWatchers(card.watchers)
      .setWatcherPosts(card.watcherPosts)
      .addAllDrops(card.drops.map(::dropOf))
      .setMaxDrops(card.maxDrops)
      .setNextDropSeconds(card.nextDropSeconds.toInt())
      .build()
  }

  private fun dropOf(drop: TreasureDrop): StrongholdTabSmsgProto.TreasureDropInfo {
    return StrongholdTabSmsgProto.TreasureDropInfo.newBuilder()
      .setMadeAtEpochMillis(drop.madeAt.toEpochMilli())
      .setCoins(drop.coins)
      .addAllGoods(drop.goods.map { (itemId, amount) ->
        StrongholdTabSmsgProto.DropGoods.newBuilder().setItemId(itemId).setAmount(amount).build()
      })
      .build()
  }
}
