package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Staff Sweep (`skills.yml` id 146): with a staff, a weapon blow at `100% + 20%/lv` ATK on every foe within 2 m of the
 * Brawler. A blow that lands pushes its foe 1 m away.
 */
@Component
class StaffSweep : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val held = world.get(casterId, CombatStats::class)?.values?.heldWeaponType

    return if (held == WeaponType.STAFF) null else OpError.SKILL_NEEDS_STAFF
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.world.heldWeaponTypeOf(ctx.casterId) == WeaponType.STAFF
  }

  override fun execute(ctx: SkillContext): Damage? {
    val brawlerAt = ctx.world.positionOf(ctx.casterId) ?: return null
    val foes = ctx.world.entitiesInCube(brawlerAt, REACH_TILES * 2).filter { it != ctx.casterId && ctx.world.isFoe(it) }

    for (foe in foes) {
      val blow = ctx.world.strike(foe, ctx.battle.usedAttack, atkPercent(ctx.skillLevel))
      if (blow != null && blow != Miss && blow != Blocked) {
        ctx.world.knockBack(foe, brawlerAt, KNOCKBACK_TILES)
      }
    }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val REACH_TILES = 2L
    const val KNOCKBACK_TILES = 1

    fun atkPercent(level: Int): Int {
      return 100 + 20 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
