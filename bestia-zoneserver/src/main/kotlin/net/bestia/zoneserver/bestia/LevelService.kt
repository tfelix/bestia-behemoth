package net.bestia.zoneserver.bestia

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.entity.ComponentNotifyService
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This service manages entities to level up and to receive exp.
 *
 * @author Thomas Felix
 */
@Service
class LevelService(
    private val statusService: StatusService,
    private val componentNotifyService: ComponentNotifyService
) {

  /**
   * Sets the level of the given entity to a certain level. The entity must
   * have a [LevelComponent] attached to it or this will throw.
   *
   * @param entity
   * The entity to set a level.
   * @param level
   * The new level to set.
   */
  fun setLevel(entity: Entity, level: Int) {
    LOG.debug { "setLevel(): $entity level to $level." }

    if (level <= 0 || level > MAX_LEVEL) {
      throw IllegalArgumentException("Level must be between 1 and $MAX_LEVEL")
    }

    val lvComp = entity.getComponent(LevelComponent::class.java)
    lvComp.level = level
    statusService.calculateStatusPoints(entity)

    componentNotifyService.notifyChanged(entity.id, lvComp)
  }

  /**
   * Returns the level of the entity. The entity must possess the
   * [LevelComponent] or 1 is returned.
   */
  fun getLevel(entity: Entity): Int {
    return entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1
  }

  /**
   * The current experience of the entity. Entity must possess
   * [LevelComponent] or 0 will be returned.
   *
   * @return The current exp or 0 if the [LevelComponent] is missing.
   */
  fun getExp(entity: Entity): Int {
    return entity.tryGetComponent(LevelComponent::class.java)?.exp ?: 0
  }

  private fun checkLevelup(entity: Entity, levelComponent: LevelComponent) {
    val neededExp = Math
        .round(Math.pow(levelComponent.level.toDouble(), 3.0) / 10 + 15.0 + levelComponent.level * 1.5).toInt()

    LOG.trace { "checkLevelup(): $entity has ${levelComponent.exp}/$neededExp exp for levelup" }

    if (levelComponent.exp > neededExp && levelComponent.level < MAX_LEVEL) {
      levelComponent.exp = levelComponent.exp - neededExp
      levelComponent.level = levelComponent.level + 1
      checkLevelup(entity, levelComponent)
    }

    statusService.calculateStatusPoints(entity)
  }

  /**
   * Adds a amount of experience points to a entity with a level component. It
   * will check if a level up has occurred and recalculate status points if
   * neccesairy.
   *
   * @param entity
   * @param exp
   */
  fun addExp(entity: Entity, exp: Int) {
    if (exp < 0) {
      throw IllegalArgumentException("Exp can not be negative.")
    }

    LOG.trace { "addExp(): $entity gains $exp exp" }

    val levelComp = entity.getComponent(LevelComponent::class.java)
    levelComp.exp = levelComp.exp + exp

    checkLevelup(entity, levelComp)

    componentNotifyService.notifyChanged(entity.id, levelComp)
  }

  companion object {
    const val MAX_LEVEL = 10
  }
}
