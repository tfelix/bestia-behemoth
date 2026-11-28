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
    val debuff: Boolean,
    /** For SATISFIED, the meal behind it. See [ActiveStatusEffect.amount]. */
    val amount: Int = 0
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
            .setAmount(entry.amount)
            .build()
        }
      )

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setEffects(effectList))
  }
}
