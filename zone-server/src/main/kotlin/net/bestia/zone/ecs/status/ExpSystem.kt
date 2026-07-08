package net.bestia.zone.ecs.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.player.Master
import org.springframework.core.annotation.Order
import kotlin.math.pow
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(60)
class ExpSystem : System {

  override val writes: Set<KClass<out Component>> = setOf(Exp::class, Level::class, SkillPoints::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Exp::class, Level::class).each { entityId ->
      val expComp = get<Exp>()
      val levelComp = get<Level>()
      val isMaster = world.has(entityId, Master::class)

      var requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
      while (expComp.value >= requiredExpNextLevel) {
        expComp.value -= requiredExpNextLevel
        levelComp.inc()

        if (isMaster) {
          world.get(entityId, SkillPoints::class)?.let { skillPoints ->
            skillPoints.value += 1
            world.markChanged(entityId, SkillPoints::class)
          }
        }

        requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
        world.markChanged(entityId, Level::class)
        world.markChanged(entityId, Exp::class)

        LOG.debug { "$entityId got level up: ${levelComp.level} (next req. exp: $requiredExpNextLevel)" }
      }
    }
  }

  private fun getRequiredExperience(level: Int): Int {
    val c = 11

    return if (level <= 10) {
      // Phase 1: 30% per step
      (c * 1.35.pow(level)).toInt()
    } else {
      // Phase 2: base at 10 (end value of phase 1)
      val baseAt10 = c * 1.35.pow(10)
      val blocks = (level - 10) / 10 // full 10er steps after 10
      val rest = (level - 10) % 10 // the rest in the block

      (baseAt10 * 1.3.pow(blocks) * 1.15.pow(rest)).toInt()
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
