package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What Backstab and Shadow Step share: they are cast from the Assassin's Hide or Cloak. */
object FromHiding {

  private val CONCEALMENTS = listOf(StatusEffectId.HIDING, StatusEffectId.CLOAKING)

  /** Refuses the cast at the button when the caster is not hidden. */
  fun refusal(world: World, casterId: EntityId): OpError? {
    val effects = world.get(casterId, StatusEffects::class)
    val isHidden = CONCEALMENTS.any { effects?.hasEffect(it.id) == true }

    return if (isHidden) null else OpError.SKILL_NEEDS_HIDING
  }

  fun isHidden(ctx: SkillContext): Boolean {
    return CONCEALMENTS.any { ctx.world.hasEffect(ctx.casterId, it.id) }
  }
}
