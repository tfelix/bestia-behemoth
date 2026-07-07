package net.bestia.zone.ecs.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Ecs2System
import net.bestia.zone.ecs2.World
import org.springframework.core.annotation.Order
import kotlin.math.pow
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(60)
class ExpSystem : Ecs2System {

  override val writes: Set<KClass<out Component>> = setOf(Exp::class, Level::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Exp::class, Level::class).each { id, expComp, levelComp ->
      var requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
      while (expComp.value >= requiredExpNextLevel) {
        expComp.value -= requiredExpNextLevel
        levelComp.inc()

        requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
        world.markChanged<Level>(id)
        world.markChanged<Exp>(id)

        LOG.debug { "$id got level up: ${levelComp.level} (next req. exp: $requiredExpNextLevel)" }
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
