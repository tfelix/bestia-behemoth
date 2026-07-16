package net.bestia.zone.ecs.battle.exp

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.level.LevelUpExperienceCalculator
import net.bestia.zone.ecs.battle.status.SkillPoints
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(60)
class ExpSystem(
  private val levelUpExpCalc: LevelUpExperienceCalculator
) : System {

  override val writes: ComponentClassSet = setOf(
    Exp::class,
    Level::class,
    SkillPoints::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(Exp::class, Level::class).each { entityId ->
      val expComp = get<Exp>()
      val levelComp = get<Level>()
      val isMaster = world.has(entityId, Master::class)

      var requiredExpNextLevel = levelUpExpCalc.getRequiredExperience(levelComp.level + 1)
      expComp.requiredExpNextLevel = requiredExpNextLevel
      while (expComp.value >= requiredExpNextLevel) {
        expComp.value -= requiredExpNextLevel
        levelComp.inc()

        if (isMaster) {
          world.get(entityId, SkillPoints::class)?.let { skillPoints ->
            skillPoints.value += 1
          }
        }

        requiredExpNextLevel = levelUpExpCalc.getRequiredExperience(levelComp.level + 1)
        expComp.requiredExpNextLevel = requiredExpNextLevel

        LOG.debug { "$entityId got level up: ${levelComp.level} (next req. exp: $requiredExpNextLevel)" }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
