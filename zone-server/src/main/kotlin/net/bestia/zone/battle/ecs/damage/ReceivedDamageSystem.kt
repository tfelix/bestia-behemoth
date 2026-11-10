package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.util.EntityId
import kotlin.random.Random
import net.bestia.zone.battle.ecs.skill.RitualParticipant
import net.bestia.zone.battle.ecs.skill.Channel
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.entity.GameMetrics
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.attack.AttackSystem
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.logout.ecs.LogoutIntent
import org.springframework.stereotype.Component as SpringComponent
import net.bestia.zone.ecs.core.update
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Dead

/**
 * Distributes the damage to the entity. It is not yet clear if we should go this approach or rather
 * go the one that a message directly attempts to calculate the damage. However it is important to
 * handle also damage this directly came from ecs entities e.g. like AOE attacks.
 */
@SpringComponent
class ReceivedDamageSystem(
  private val metrics: GameMetrics = GameMetrics(),
  private val reactions: DamageReactions = DamageReactions.NONE,
  private val random: Random = Random.Default,
) : System {
  override val phase = Phase.COMBAT
  override val after = setOf(AttackSystem::class)

  // A lifesteal heals the dealer, weighing its heal-received modifier.
  override val reads: ComponentClassSet = DamageGate.READS + CombatStats::class
  override val writes: ComponentClassSet =
    setOf(
      Health::class, TakenDamage::class, Dead::class, LogoutIntent::class, Casting::class, Crafting::class, Channel::class,
      RitualParticipant::class,
      InCombat::class, IncomingDamage::class, Animation::class,
      // A barrier or a freeze reacts to the hit, and may leave.
      StatusEffects::class, IsStatusValueDirty::class
    )

  override fun update(world: World, deltaTime: Float) {
    world.query(IncomingDamage::class, Health::class).each { id ->
      val receivedDamage = get<IncomingDamage>()
      val staged = receivedDamage.drain()
      val health = get<Health>()

      // Removed only if still empty: the deferred queue is FIFO, so a hit that an earlier system deferred
      // this tick lands on this same instance first. Removing it unconditionally would delete that hit.
      world.defer {
        if (receivedDamage.amounts.isEmpty()) {
          world.remove(id, IncomingDamage::class)
        }
      }

      // Drained and dropped, not skipped before the drain: leaving the hits on would have the blow
      // land again on the next tick, forever. Nothing else follows either - no aggro record, no combat
      // timer - because none of it means anything to something that cannot be hurt.
      if (DamageGate.isImmune(world, id)) return@each

      val hits = staged.map { it.copy(amount = reactions.onDamageTaken(world, id, it)) }

      val takenDamage = world.get(id, TakenDamage::class) ?: world.add(id, TakenDamage())
      hits.forEach { takenDamage.addDamage(it.sourceEntityId, it.amount) }
      takenDamage.removeOldEntries()

      val total = hits.sumOf { it.amount }
      metrics.hits(hits.size, total.toLong())
      health.current -= total
      hits.forEach { reactions.onDamageDealt(world, it.sourceEntityId, it.amount) }

      // Taking damage aborts a pending logout and interrupts a running cast or craft. Removing the
      // component is what notifies the client (via the generic component-removed message); done inline
      // since we already hold the world rather than going through the cancel services.
      if (total > 0) {
        world.update(id, { InCombat() }) { it.remainingSeconds = InCombat.TIMEOUT_SECONDS }

        if (world.has(id, LogoutIntent::class)) {
          world.remove(id, LogoutIntent::class)
        }
        if (world.has(id, Casting::class) && !resistsInterrupt(world, id)) {
          Casting.end(world, id)
        }
        if (world.has(id, Channel::class) || world.has(id, RitualParticipant::class)) {
          world.remove(id, Channel::class)
          world.remove(id, RitualParticipant::class)
          world.get(id, StatusEffects::class)?.removeEffect(StatusEffectId.CHANNELING.id)
          world.add(id, IsStatusValueDirty)
        }
        if (world.has(id, Crafting::class)) {
          world.remove(id, Crafting::class)
        }
        // A hit stands an emoting entity up, the same tick it lands.
        world.get(id, Animation::class)?.endEmote()
      }

      if (health.current == 0 && Dead.markOnce(world, id)) {
        LOG.trace { "$id died due to damage." }
      }
    }
  }

  /** A focused caster may keep casting through a blow, see `CombatValues.castInterruptResistance`. */
  private fun resistsInterrupt(world: World, id: EntityId): Boolean {
    val resistance = world.get(id, CombatStats::class)?.values?.castInterruptResistance ?: 0

    return resistance > 0 && random.nextInt(100) < resistance
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
