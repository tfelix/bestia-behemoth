package net.bestia.zone.item.ecs

import net.bestia.bnet.proto.LootClaimComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.AccountId

data class LootClaimComponentSMSG(
  override val entityId: Long,
  val holders: Set<AccountId>,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val claim = LootClaimComponentSMSGProto.LootClaimComponentSMSG.newBuilder()
      .addAllHolderAccountIds(holders)
      .setRemoved(removed)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setLootClaim(claim))
  }
}
