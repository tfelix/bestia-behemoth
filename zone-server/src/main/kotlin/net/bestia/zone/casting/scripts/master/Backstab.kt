package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Backstab (`skills.yml` id 128): a weapon blow at `200% + 50%/lv` ATK, only from Hide or Cloak and only from behind
 * the target. Like any cast it ends the hiding.
 */
@Component
class Backstab(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return FromHiding.refusal(world, casterId)
  }

  override fun checkTarget(
    world: World,
    casterId: EntityId,
    targetEntityId: EntityId?,
    skillLevel: Int,
    args: List<Long>
  ): OpError? {
    val target = targetEntityId ?: return null

    return if (standsBehind(world, casterId, target)) null else OpError.SKILL_NEEDS_BEHIND
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) &&
        FromHiding.isHidden(ctx) && ctx.world.isBehind(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.world.strike(target, ctx.battle.usedAttack, atkPercent(ctx.skillLevel))

    return null
  }

  private fun standsBehind(world: World, casterId: EntityId, targetId: EntityId): Boolean {
    val facing = world.get(targetId, Facing::class) ?: return false
    val targetAt = world.get(targetId, Position::class)?.toVec3L() ?: return false
    val casterAt = world.get(casterId, Position::class)?.toVec3L() ?: return false

    return facing.isBehind(targetAt, casterAt)
  }

  companion object {
    private const val MAX_LEVEL = 5

    fun atkPercent(level: Int): Int {
      return 200 + 50 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }
  }
}
