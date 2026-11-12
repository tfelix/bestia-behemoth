package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Shield Wall (`skills.yml` id 135): with a shield, the Knight takes up [StatusEffectId.SHIELD_WALL] until a recast. */
@Component
class ShieldWall : SkillStrategy {

  override val toggledStatus: StatusEffectId = StatusEffectId.SHIELD_WALL

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val shield = world.get(casterId, CombatStats::class)?.values?.shieldArmorType

    return if (shield == null) OpError.SKILL_NEEDS_SHIELD else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.world.holdsShield(ctx.casterId)
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.SHIELD_WALL.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  private companion object {
    const val MAX_LEVEL = 5
  }
}
