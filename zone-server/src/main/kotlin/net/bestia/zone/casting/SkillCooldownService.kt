package net.bestia.zone.casting

import net.bestia.zone.casting.ecs.SkillCooldowns
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * Keeps a caster from using a skill again before its cooldown has passed. Checked when a cast starts and again
 * when it resolves, because a channelled cast and a second activation can overlap.
 *
 * The length comes from the cast's [SkillLevelCost], not from the catalogue row, since a skill may recover
 * faster at a higher level. The owner's client is told how long, so its hotbar counts the same span down.
 */
@Service
class SkillCooldownService(
  private val clock: Clock = Clock.systemUTC(),
  private val messages: OutMessageProcessor? = null,
) {

  fun isReady(world: World, casterId: EntityId, skillId: Long): Boolean {
    val cooldowns = world.get(casterId, SkillCooldowns::class) ?: return true

    return cooldowns.isReady(skillId, clock.millis())
  }

  fun start(world: World, casterId: EntityId, skillId: Long, seconds: Float) {
    if (seconds <= 0f) {
      return
    }

    val cooldowns = world.get(casterId, SkillCooldowns::class)
      ?: world.add(casterId, SkillCooldowns())
    val remainingMillis = (seconds * MILLIS_PER_SECOND).toLong()
    cooldowns.start(skillId, clock.millis() + remainingMillis)

    // A wild creature has no player to tell.
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    messages?.sendToPlayer(accountId, SkillCooldownSMSG(casterId, skillId, remainingMillis))
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000f
  }
}
