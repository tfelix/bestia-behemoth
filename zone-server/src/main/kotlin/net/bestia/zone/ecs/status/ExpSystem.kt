package net.bestia.zone.ecs.status

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ComponentSet
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.stereotype.Component
import kotlin.math.pow

@Component
class ExpSystem : IteratingSystem() {
  override val requiredComponents: ComponentSet = setOf(
    Exp::class,
    Level::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    val levelComp = entity.getOrThrow(Level::class)
    val expComp = entity.getOrThrow(Exp::class)

    var requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
    while (expComp.value >= requiredExpNextLevel) {
      expComp.value -= requiredExpNextLevel
      levelComp.inc()

      requiredExpNextLevel = getRequiredExperience(levelComp.level + 1)
      entity.add(IsDirty)

      LOG.debug { "$entity got level up: ${levelComp.level} (next req. exp: $requiredExpNextLevel)" }
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