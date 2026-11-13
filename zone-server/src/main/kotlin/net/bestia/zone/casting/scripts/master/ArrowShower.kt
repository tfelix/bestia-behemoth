package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Arrow Shower (`skills.yml` id 156): one arrow loosed high, coming down on every foe in the 3x3 tiles aimed at for
 * `80% + 10%/lv` ATK. A shot that lands pushes its foe 1 m away from the Hunter.
 */
@Component
class ArrowShower(
  losService: LineOfSightService,
  ammo: AmmoSupply,
) : BowSkillStrategy(losService, ammo) {

  override fun execute(ctx: SkillContext): Damage? {
    val shot = nockArrow(ctx) ?: return null
    val hunterAt = ctx.battle.attacker.position
    val foes = ctx.world.entitiesInCube(ctx.aimedAt, AREA_EDGE).filter { ctx.world.isFoe(it) }

    for (foe in foes) {
      if (ctx.world.strike(foe, shot, atkPercent(ctx.skillLevel)).landed()) {
        ctx.world.knockBack(foe, hunterAt, KNOCKBACK_TILES)
      }
    }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val AREA_EDGE = 3L
    const val KNOCKBACK_TILES = 1

    fun atkPercent(level: Int): Int {
      return 80 + 10 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
