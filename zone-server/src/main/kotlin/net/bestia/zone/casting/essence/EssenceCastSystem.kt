package net.bestia.zone.casting.essence

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.damage.LandedHits
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.EssenceCast
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.ecs.construction.ConstructionSystem
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import java.util.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * A boss essence that casts by itself: a weapon blow landing on or from its wearer may cast the boss's attack, free,
 * at skill level 1 like a Reflex. Reads the hits of the tick before.
 */
@SpringComponent
class EssenceCastSystem(
  private val hits: EssenceCastHits,
  private val skills: SkillExecutionService,
  private val skillIds: SkillIds,
  private val random: Random = ThreadLocalRandomSource,
) : System {

  override val phase = Phase.ACTIONS
  override val after = setOf(ConstructionSystem::class)
  override val reads: ComponentClassSet = setOf(CombatStats::class)

  private class Fired(val casterId: EntityId, val skillId: Long, val spotId: EntityId)

  override fun update(world: World, deltaTime: Float) {
    val fired = hits.drain().flatMap { hit -> castsOf(world, hit) }
    if (fired.isEmpty()) return

    // Between ticks, for the reason ReflexSystem gives; where the spot stands is read there too.
    world.post {
      fired.filter { isAlive(it.casterId) && !has(it.casterId, Dead::class) }.forEach { cast ->
        val spot = get(cast.spotId, Position::class)?.toVec3L() ?: return@forEach
        skills.executeFree(this, cast.casterId, cast.skillId, SKILL_LEVEL, cast.spotId, spot)
      }
    }
  }

  private fun castsOf(world: World, hit: LandedHits.LandedHit): List<Fired> {
    val striker = castsOf(world, wearerId = hit.sourceId, foeId = hit.targetId, hit) { it.trigger.wearerStrikes }
    val struck = castsOf(world, wearerId = hit.targetId, foeId = hit.sourceId, hit) { !it.trigger.wearerStrikes }

    return striker + struck
  }

  private fun castsOf(
    world: World,
    wearerId: EntityId,
    foeId: EntityId,
    hit: LandedHits.LandedHit,
    side: (EssenceCast) -> Boolean,
  ): List<Fired> {
    val casts = world.get(wearerId, CombatStats::class)?.values?.essenceCasts.orEmpty()

    return casts.filter { side(it) && it.trigger.matches(hit.kind) && random.nextInt(100) < it.chancePercent }
      .mapNotNull { cast ->
        val skillId = skillIds.idOf(cast.skill) ?: return@mapNotNull null
        val spotId = if (cast.at == EssenceCast.Spot.WEARER) wearerId else foeId
        Fired(wearerId, skillId, spotId)
      }
  }

  private companion object {
    const val SKILL_LEVEL = 1
  }
}
