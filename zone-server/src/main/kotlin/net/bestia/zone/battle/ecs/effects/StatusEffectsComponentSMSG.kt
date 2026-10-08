package net.bestia.zone.battle.ecs.effects

import net.bestia.bnet.proto.StatusEffectListSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class StatusEffectsComponentSMSG(
  override val entityId: Long,
  val effects: List<StatusEffectEntry>
) : EntitySMSG {

  data class StatusEffectEntry(
    val effectId: Long,
    val level: Int,
    val remainingSeconds: Float,
    val debuff: Boolean
  )

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val effectList = StatusEffectListSMSGProto.StatusEffectListSMSG.newBuilder()
      .addAllEffects(
        effects.map { entry ->
          StatusEffectListSMSGProto.StatusEffectEntry.newBuilder()
            .setEffectId(entry.effectId.toInt())
            .setLevel(entry.level)
            .setRemainingSeconds(entry.remainingSeconds)
            .setDebuff(entry.debuff)
            .build()
        }
      )

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setEffects(effectList))
  }
}
