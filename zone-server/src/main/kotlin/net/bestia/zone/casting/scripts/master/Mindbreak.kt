package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.Mindbroken
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Mindbreak (`skills.yml` id 118): puts [StatusEffectId.MINDBREAK] on one of the caster's own bestias. Arcane Mastery
 * raises the MATK it gives by its own percent.
 */
@Component
class Mindbreak(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  /** A bestia never changes hands, so the check at activation holds for the whole cast. */
  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>,
  ): OpError? {
    val masterId = world.get(casterId, Master::class)?.masterId
    val ownerId = targetEntityId?.let { world.get(it, OwnedBestia::class)?.masterId }

    return if (masterId != null && masterId == ownerId) null else OpError.SKILL_NEEDS_OWN_BESTIA
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val bestia = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.applyStatusEffect(bestia, StatusEffectId.MINDBREAK.id, level, amount = matkPercent(level, battle))

    return null
  }

  companion object {
    fun matkPercent(level: Int, battle: EntityBattleContext): Int {
      return Mindbroken.percentOfLevel(level) * (100 + battle.attacker.arcaneDamagePercent) / 100
    }

    private const val MAX_LEVEL = 3
  }
}
