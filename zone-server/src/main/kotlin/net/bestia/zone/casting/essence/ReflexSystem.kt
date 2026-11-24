package net.bestia.zone.casting.essence

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.prop.ecs.construction.ConstructionSystem
import net.bestia.zone.skill.SkillIds
import java.util.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * A Reflex essence: a blow or spell that lands on its wearer may cast the species' buff on them, free, at skill
 * level 1. Reads the hits of the tick before, like the item spell bindings do.
 */
@SpringComponent
class ReflexSystem(
  private val hits: ReflexHits,
  private val skills: SkillExecutionService,
  private val skillIds: SkillIds,
  private val bestias: BestiaCatalogue,
  private val random: Random = ThreadLocalRandomSource,
) : System {

  override val phase = Phase.ACTIONS
  override val after = setOf(ConstructionSystem::class)
  override val reads: ComponentClassSet = setOf(CombatStats::class)

  override fun update(world: World, deltaTime: Float) {
    val casts = hits.drain().distinct().flatMap { struckId -> castsOf(world, struckId) }
    if (casts.isEmpty()) return

    // Between ticks: whether the wearer died of the very blow is not this system's to read.
    world.post {
      casts.filter { (struckId, _) -> isAlive(struckId) && !has(struckId, Dead::class) }.forEach { (struckId, skillId) ->
        skills.executeFree(this, struckId, skillId, SKILL_LEVEL, targetEntityId = struckId, targetPosition = null)
      }
    }
  }

  /** The buffs [struckId] casts this time, as (wearer, skill id). */
  private fun castsOf(world: World, struckId: Long): List<Pair<Long, Long>> {
    val reflexes = world.get(struckId, CombatStats::class)?.values?.reflexes.orEmpty()

    return reflexes.mapNotNull { reflex ->
      val skillId = skillIds.idOf(reflex.skill) ?: return@mapNotNull null
      val attackLevel = bestias.attackLevelOf(skillId) ?: 0
      val fires = attackLevel <= reflex.attackLevelCap && random.nextInt(100) < reflex.chancePercent

      (struckId to skillId).takeIf { fires }
    }
  }

  private companion object {
    const val SKILL_LEVEL = 1
  }
}
