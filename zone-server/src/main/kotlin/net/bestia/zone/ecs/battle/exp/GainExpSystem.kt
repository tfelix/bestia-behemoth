package net.bestia.zone.ecs.battle.exp

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.persistence.MasterEntityPersistenceService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.level.LevelUpExperienceCalculator
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.SkillPoints
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(60)
class GainExpSystem(
  private val levelUpExpCalc: LevelUpExperienceCalculator,
  private val masterEntityPersistenceService: MasterEntityPersistenceService,
) : System {

  override val reads: ComponentClassSet = setOf(
    Master::class,
    GainExp::class
  )

  override val writes: ComponentClassSet = setOf(
    Exp::class,
    Level::class,
    SkillPoints::class,
    IsStatusValueDirty::class
  )

  override fun update(world: World, deltaTime: Float) {
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
        }

        LOG.debug { "$entityId got level up: ${levelComp.level} (next req. exp: ${expComp.requiredExpNextLevel})" }
      }

      // A higher level raises the formula-driven condition pools; flag a recalc so
      // StatusValueRecalcSystem rebuilds max HP/Mana/Stamina from the new level next tick.
      if (leveledUp) {
        world.add(entityId, IsStatusValueDirty)
      }

      if (isMaster) {
        val masterId = world.get(entityId, Master::class)?.masterId
        if (masterId == null) {
          LOG.warn { "Entity $entityId has no Master component, cannot persist its level/exp" }
        } else {
          // Runs synchronously on the tick thread (blocking DB write) until this gets a
          // centralized async dispatch mechanism.
          masterEntityPersistenceService.persistEntity(world, entityId, masterId)
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
