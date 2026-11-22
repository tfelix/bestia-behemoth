package net.bestia.zone.stronghold.ecs

import net.bestia.zone.battle.ecs.damage.PlayerDeathSystem
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.GuildAffiliation
import net.bestia.zone.settlement.ecs.CitySignSystem
import net.bestia.zone.stronghold.Empyrites
import org.springframework.stereotype.Component as SpringComponent

/**
 * Hands a broken Empyrite's Stronghold to the guild that struck the breaking blow, heals one nobody has hit for a
 * while, and writes its health now and then so a restart does not heal it.
 *
 * In the death phase before `DeathSystem` removes the dead, so a broken Empyrite is seen before it is gone.
 */
@SpringComponent
class EmpyriteSystem(
  private val empyrites: Empyrites,
) : System {

  override val phase = Phase.DEATH
  override val before = setOf(PlayerDeathSystem::class)
  override val after = setOf(CitySignSystem::class)

  // Steward: a broken Empyrite sends its steward away.
  override val reads: ComponentClassSet = setOf(Dead::class, TakenDamage::class, Steward::class) + GuildAffiliation.READS
  override val writes: ComponentClassSet = setOf(Empyrite::class, Health::class)

  private var sinceRecorded = 0f

  override fun update(world: World, deltaTime: Float) {
    sinceRecorded += deltaTime
    val record = sinceRecorded >= RECORD_SECONDS
    if (record) sinceRecorded = 0f

    world.query(Empyrite::class, Health::class).each { id ->
      val empyrite = get<Empyrite>()
      val health = get<Health>()

      if (world.has(id, Dead::class)) {
        val breaker = world.get(id, TakenDamage::class)?.mostRecentAttacker(BREAKING_BLOW_MS)
        val breakerGuildId = breaker?.let { GuildAffiliation.membershipOf(world, it)?.guildId }
        empyrites.broken(world, empyrite.rank, breakerGuildId)
        return@each
      }

      if (health.current < empyrite.lastHp) empyrites.struck(world, empyrite.rank)
      heal(empyrite, health, deltaTime)
      if (record) empyrites.recordHp(empyrite.rank, health.current)
    }
  }

  private fun heal(empyrite: Empyrite, health: Health, deltaTime: Float) {
    if (health.current < empyrite.lastHp) {
      empyrite.unhurtSeconds = 0f
      empyrite.pendingHeal = 0f
    } else {
      empyrite.unhurtSeconds += deltaTime
    }

    if (empyrite.unhurtSeconds >= HEAL_AFTER_SECONDS && health.current < health.max) {
      empyrite.pendingHeal += health.max * HEAL_SHARE_PER_SECOND * deltaTime
      val whole = empyrite.pendingHeal.toInt()
      health.current += whole
      empyrite.pendingHeal -= whole
    }

    empyrite.lastHp = health.current
  }

  private companion object {
    /** The docs' 5 minutes unhit before the Empyrite heals 1% a minute. */
    const val HEAL_AFTER_SECONDS = 300f
    const val HEAL_SHARE_PER_SECOND = 0.01f / 60f

    const val RECORD_SECONDS = 30f

    /** The blow that broke it landed this tick; a minute is room for a blow whose source left at once. */
    const val BREAKING_BLOW_MS = 60_000L
  }
}
