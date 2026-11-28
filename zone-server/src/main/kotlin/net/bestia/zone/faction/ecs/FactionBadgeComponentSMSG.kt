package net.bestia.zone.faction.ecs

import net.bestia.bnet.proto.FactionBadgeComponentSMSGProto
import net.bestia.bnet.proto.FactionBadgeComponentSMSGProto.FactionId
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.worldgen.core.Faction
import net.bestia.zone.message.EntitySMSG

data class FactionBadgeComponentSMSG(
  override val entityId: Long,
  val faction: Faction,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val badge = FactionBadgeComponentSMSGProto.FactionBadgeComponentSMSG.newBuilder()
      .setFaction(wireIdOf(faction))
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setFactionBadge(badge))
  }

  private fun wireIdOf(faction: Faction): FactionId {
    return when (faction) {
      Faction.CHAOS -> FactionId.FACTION_CHAOS
      Faction.ETERNITY -> FactionId.FACTION_ETERNITY
      Faction.CIRCLE -> FactionId.FACTION_CIRCLE
    }
  }
}
