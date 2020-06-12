package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent

private val LOG = KotlinLogging.logger { }

class IsEntityAttackableCheck(
    private val battleCtx: EntityBattleContext
) : AttackCheck() {
  /**
   * It must be checked if an entity is eligible for receiving damage. This
   * means that an [StatusComponent] as well as a
   * [PositionComponent] must be present.
   *
   * @return TRUE if the entity is abtle to receive damage. FALSE otherwise.
   */
  override fun checkAttackCondition(): Boolean {
    battleCtx.defender
    // Check if we have valid x and y.
    if (!battleCtx.defender.hasComponent(StatusComponent::class.java)) {
      LOG.warn("Entity {} does not have status component.", battleCtx.defender)
      return false
    }

    if (!battleCtx.defender.hasComponent(PositionComponent::class.java)) {
      LOG.warn("Entity {} does not have position component.", battleCtx.defender)
      return false
    }

    if (!battleCtx.defender.hasComponent(LevelComponent::class.java)) {
      LOG.warn("Entity {} does not have level component.", battleCtx.defender)
      return false
    }

    return true
  }
}