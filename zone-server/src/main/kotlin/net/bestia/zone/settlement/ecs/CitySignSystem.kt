package net.bestia.zone.settlement.ecs

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ends the town of a destroyed sign, and heals a sign nobody has hit for a while.
 *
 * In the death phase before `DeathSystem` removes the dead, so a destroyed sign is seen before it is gone.
 */
@SpringComponent
class CitySignSystem(
  private val towns: TownService,
) : System {

  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)

  override val reads: ComponentClassSet = setOf(Dead::class)
  override val writes: ComponentClassSet = setOf(CitySign::class, Health::class)

  private var sinceRecorded = 0f

  override fun update(world: World, deltaTime: Float) {
    sinceRecorded += deltaTime
    val record = sinceRecorded >= RECORD_SECONDS
    if (record) sinceRecorded = 0f

    world.query(CitySign::class, Health::class).each { id ->
      val sign = get<CitySign>()
      val health = get<Health>()

      if (world.has(id, Dead::class)) {
        towns.fall(sign.townId)
        return@each
      }

      heal(sign, health, deltaTime)
      if (record) towns.recordSignHp(sign.townId, health.current)
    }
  }

  private fun heal(sign: CitySign, health: Health, deltaTime: Float) {
    if (health.current < sign.lastHp) {
      sign.unhurtSeconds = 0f
      sign.pendingHeal = 0f
    } else {
      sign.unhurtSeconds += deltaTime
    }

    if (sign.unhurtSeconds >= HEAL_AFTER_SECONDS && health.current < health.max) {
      sign.pendingHeal += health.max * HEAL_SHARE_PER_SECOND * deltaTime
      val whole = sign.pendingHeal.toInt()
      health.current += whole
      sign.pendingHeal -= whole
    }

    sign.lastHp = health.current
  }

  private companion object {
    /** The docs' 5 minutes unhit before the sign heals 1% a minute. */
    const val HEAL_AFTER_SECONDS = 300f
    const val HEAL_SHARE_PER_SECOND = 0.01f / 60f

    const val RECORD_SECONDS = 30f
  }
}
