package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Provoke (`skills.yml` id 134): a monster or a bestia, owned or wild, fights the Knight for `3 s + 1 s/lv`. A master
 * cannot be forced. A newer provoke takes the creature from whoever provoked it before.
 */
@Component
class Provoke(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>
  ): OpError? {
    val target = targetEntityId ?: return null

    return if (world.has(target, Master::class)) OpError.SKILL_NOT_ON_MASTERS else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    provoke(ctx.world, target, ctx.skillLevel)

    return null
  }

  companion object {

    /** Makes [target] fight the caster, for [durationSeconds] or as long as Provoke at [level] lasts. Not a master. */
    fun provoke(world: SkillWorld, target: EntityId, level: Int, durationSeconds: Double? = null) {
      if (world.masterIdOf(target) != null) return

      // A refresh would keep the old provoker, see StatusEffects.applyEffect.
      world.removeEffect(target, StatusEffectId.PROVOKED.id)
      world.applyStatusEffect(target, StatusEffectId.PROVOKED.id, level, durationSeconds = durationSeconds)
    }
  }
}
