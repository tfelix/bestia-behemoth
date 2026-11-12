package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.PartyMates
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Devotion (`skills.yml` id 171): puts [StatusEffectId.DEVOTED] on a party member, a bestia of the party or one of the
 * Knight's own, never the Knight. A newer Devotion takes the ally from whoever devoted it before.
 */
@Component
class Devotion(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>
  ): OpError? {
    val ally = targetEntityId?.takeIf { it != casterId }

    return if (ally != null && PartyMates.isPartyOf(world, casterId, ally)) null else OpError.SKILL_NEEDS_PARTY_MEMBER
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val ally = ctx.targetEntityId?.takeIf { it != ctx.casterId } ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isPartyOf(ally) &&
        !ctx.world.isFallen(ally)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ally = ctx.targetEntityId ?: return null

    // A refresh would keep the old Knight, see StatusEffects.applyEffect.
    ctx.world.removeEffect(ally, StatusEffectId.DEVOTED.id)
    ctx.world.applyStatusEffect(ally, StatusEffectId.DEVOTED.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
