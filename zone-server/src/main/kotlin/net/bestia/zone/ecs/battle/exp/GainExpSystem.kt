package net.bestia.zone.ecs.battle.exp

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.level.LevelUpExperienceCalculator
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.persistence.EntityWriteBehind
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
class GainExpSystem(
  private val levelUpExpCalc: LevelUpExperienceCalculator,
  private val writeBehind: EntityWriteBehind,
) : System {
  override val phase = Phase.ITEMS

  override val reads: ComponentClassSet = setOf(
    Master::class,
    GainExp::class
  ) + EntityWriteBehind.READS

  override val writes: ComponentClassSet = setOf(
    Exp::class,
    Level::class,
    SkillPoints::class,
    StatusPoints::class,
    IsStatusValueDirty::class
  )

  override fun update(world: World, deltaTime: Float) {
    val gainedMasters = mutableListOf<EntityId>()

    world.query(GainExp::class, Exp::class, Level::class).each { entityId ->
      val gainExpComp = get<GainExp>()
      val expComp = get<Exp>()
      val levelComp = get<Level>()
      val isMaster = world.has(entityId, Master::class)

      expComp.value += gainExpComp.value
      world.remove(entityId, GainExp::class)

      var leveledUp = false
      while (expComp.value >= expComp.requiredExpNextLevel) {
        expComp.value -= expComp.requiredExpNextLevel
        levelComp.inc()
        leveledUp = true
        expComp.requiredExpNextLevel = levelUpExpCalc.getRequiredExperience(levelComp.level)

        if (isMaster) {
          world.get(entityId, SkillPoints::class)?.let { skillPoints ->
            skillPoints.value += 1
          }
          // The docs' `effGain = 5 + floor(reachedLevel / 2)`: a level's worth of status points has to
          // keep up with an escalating effort value cost, so it grows with the level reached rather
          // than staying at a flat +1.
          // https://docs.bestia-game.net/docs/mechanics/statusvalues/#effort-values
          world.get(entityId, StatusPoints::class)?.let { statusPoints ->
            statusPoints.value += 5 + levelComp.level / 2
          }
        }

        LOG.debug { "$entityId got level up: ${levelComp.level} (next req. exp: ${expComp.requiredExpNextLevel})" }
      }

      // A higher level raises the formula-driven condition pools; flag a recalc so
      // StatusValueRecalcSystem rebuilds max HP/Mana/Stamina from the new level next tick.
      if (leveledUp) {
        world.add(entityId, IsStatusValueDirty)
      }

      if (isMaster) {
        gainedMasters.add(entityId)
      }
    }

    // Every gain is made durable, but off the tick: at most one write per master per tick.
    if (gainedMasters.isNotEmpty()) {
      writeBehind.persist(world, gainedMasters, withStatusEffects = false)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
