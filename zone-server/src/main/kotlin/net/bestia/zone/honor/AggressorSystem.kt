package net.bestia.zone.honor

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.ecs.attack.AttackSystem
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.damage.ReceivedDamageSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import org.springframework.stereotype.Component

/**
 * Marks the master who starts a fight between players as [StatusEffectId.AGGRESSOR], so fighting back costs no
 * honor. Hitting an aggressor is defense, whoever it is that hits them.
 *
 * Reads the hits before [ReceivedDamageSystem] drains them.
 */
@Component
class AggressorSystem(
  private val masters: MasterEntityResolver,
  private val aggression: Aggression,
  private val statusEffects: StatusEffectService,
) : System {
  override val phase = Phase.COMBAT
  override val after = setOf(AttackSystem::class)
  override val before = setOf(ReceivedDamageSystem::class)

  override val reads: ComponentClassSet =
    setOf(IncomingDamage::class, Account::class, Master::class, OwnedBestia::class) + DamageGate.READS
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(IncomingDamage::class, Account::class).each { victim ->
      val victimAccount = get<Account>().accountId
      val attackers = get<IncomingDamage>().amounts
        .filter { it.amount > 0 }
        .map { it.sourceEntityId }
        .distinct()

      if (attackers.isEmpty() || DamageGate.isImmune(world, victim) || aggression.isAggressor(world, victim)) {
        return@each
      }

      for (attacker in attackers) {
        val attackerAccount = world.get(attacker, Account::class)?.accountId ?: continue
        if (attackerAccount == victimAccount) {
          continue
        }
        val attackerMaster = masters.masterEntityOf(world, attacker) ?: continue

        statusEffects.applyEffect(world, attackerMaster, StatusEffectId.AGGRESSOR, level = 1)
      }
    }
  }
}
