package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Aqua Benedicta (`skills.yml` id 80): blesses the water the Priest stands in into one Holy Water. The Empty
 * Bottle it fills is the skill's reagent.
 */
@Component
class AquaBenedicta(private val water: RegionGrid) : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return null

    return if (water.isWetAt(at)) null else OpError.SKILL_NEEDS_WATER
  }

  /** Asked again: the Priest may have walked out of the water during the cast. */
  override fun isCastPossible(ctx: SkillContext): Boolean {
    val at = ctx.world.positionOf(ctx.casterId) ?: return false

    return water.isWetAt(at)
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.giveItem(HOLY_WATER_ITEM_ID, 1)

    return null
  }

  companion object {
    /** `holy_water` in `items.yml`. */
    const val HOLY_WATER_ITEM_ID = 110L
  }
}
