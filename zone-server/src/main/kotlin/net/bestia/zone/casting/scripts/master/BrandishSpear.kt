package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Facing
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Brandish Spear (`skills.yml` id 172): with a spear or a polearm, a weapon blow at `120% + 40%/lv` ATK on every foe in
 * the 3x3 tiles in front of the Knight, turned towards the foe it was aimed at.
 */
@Component
class BrandishSpear(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val held = world.get(casterId, CombatStats::class)?.values?.heldWeaponType

    return if (held in LONG_WEAPONS) null else OpError.SKILL_NEEDS_SPEAR
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) &&
        ctx.world.heldWeaponTypeOf(ctx.casterId) in LONG_WEAPONS
  }

  override fun execute(ctx: SkillContext): Damage? {
    val area = frontArea(ctx.battle.attacker.position, ctx.aimedAt)
    val foes = ctx.world.entitiesInCube(area, AREA_EDGE).filter { ctx.world.isFoe(it) }

    foes.forEach { ctx.world.strike(it, ctx.battle.usedAttack, atkPercent(ctx.skillLevel)) }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val AREA_EDGE = 3L

    private val LONG_WEAPONS = setOf(WeaponType.SPEAR, WeaponType.POLEARM)

    fun atkPercent(level: Int): Int {
      return 120 + 40 * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    /** The centre of the 3x3 tiles that start next to a Knight at [knightAt] and reach away towards [aimedAt]. */
    fun frontArea(knightAt: Vec3L, aimedAt: Vec3L): Vec3L {
      val facing = Facing().apply { turnTowards(knightAt, aimedAt) }

      return facing.ahead(knightAt, tiles = 2)
    }
  }
}
