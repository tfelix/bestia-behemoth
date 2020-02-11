package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.*
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.BattleDamageComponent
import net.bestia.zoneserver.entity.component.ConditionComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class DealDamageService() {
  fun takeDamage(receiver: Entity, damage: Damage, sender: Entity? = null) {
    when (damage) {
      is TrueDamage -> takeTrueDamage(receiver, damage, sender)
      is Hit, is CriticalHit -> takeNormalDamage(receiver, damage, sender)
      is Heal -> takeHeal(receiver, damage, sender)
    }
  }

  private fun takeHeal(receiver: Entity, heal: Heal, sender: Entity? = null) {
    val statusComp = receiver.getComponent(ConditionComponent::class.java)
    statusComp.conditionValues.addHealth(heal.amount)
  }

  /**
   * The true damage is applied directly to the entity without further
   * reducing the damage via armor.
   *
   * @param receiver
   * @param trueDamage
   */
  private fun takeTrueDamage(receiver: Entity, damage: TrueDamage, sender: Entity? = null) {
    val statusComp = receiver.getComponent(ConditionComponent::class.java)
    statusComp.conditionValues.addHealth(-damage.amount)
  }

  /**
   * This will perform a check damage for reducing it and alter all possible
   * status effects and then apply the damage to the entity. If its health
   * sinks below 0 then the [.killEntity] method will be triggered. It will
   * also trigger any attached script trigger for received damage this is
   * onTakeDamage and onApplyDamage.
   *
   * @param primaryDamage The damage to apply to this entity.
   * @return The actually applied damage.
   */
  private fun takeNormalDamage(defender: Entity, damage: Damage, attacker: Entity? = null): Damage {
    LOG.trace { "Entity $defender takes damage: $damage from: $attacker" }

    val conditionComp = defender.getComponent(ConditionComponent::class.java)

    // TODO Possibly reduce the damage via effects or scripts.

    // Hit the entity and add the origin entity into the list of received
    // damage dealers.
    val battleComp = defender.tryGetComponent(BattleDamageComponent::class.java)
        ?: BattleDamageComponent(defender.id)

    attacker?.let {
      battleComp.addDamageReceived(it.id, damage.amount)
    }

    val condValues = conditionComp.conditionValues
    condValues.addHealth(-damage.amount)

    return damage
  }
}