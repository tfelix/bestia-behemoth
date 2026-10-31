package net.bestia.zone.casting

import net.bestia.zone.casting.ecs.SkillCooldowns
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Keeps a caster from using a skill again before its catalogued `cooldown` has passed. Checked when a cast
 * starts and again when it resolves, because a channelled cast and a second activation can overlap.
 */
@Service
class SkillCooldownService(
  private val clock: Clock = Clock.systemUTC(),
) {

  fun isReady(world: World, casterId: EntityId, skill: Skill): Boolean {
    if (skill.cooldown <= 0f) {
      return true
    }

    val cooldowns = world.get(casterId, SkillCooldowns::class) ?: return true

    return cooldowns.isReady(skill.id, clock.millis())
  }

  fun start(world: World, casterId: EntityId, skill: Skill) {
    if (skill.cooldown <= 0f) {
      return
    }

    val cooldowns = world.get(casterId, SkillCooldowns::class)
      ?: world.add(casterId, SkillCooldowns())

    cooldowns.start(skill.id, clock.millis() + (skill.cooldown * MILLIS_PER_SECOND).toLong())
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000f
  }
}
