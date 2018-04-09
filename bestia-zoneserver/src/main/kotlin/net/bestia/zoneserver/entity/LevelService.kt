package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.LevelComponent
import net.bestia.zoneserver.battle.StatusService
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * This service manages entities to level up and to receive exp.
 *
 * @author Thomas Felix
 */
@Service
class LevelService(
        private val entityService: EntityService,
        private val statusService: StatusService) {

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

    LOG.debug { "Setting entity $entity level to {}$level." }

    Objects.requireNonNull(entity)

    if (level <= 0 || level > MAX_LEVEL) {
      throw IllegalArgumentException("Level must be between 1 and $MAX_LEVEL")
    }

    val lvComp = entityService.getComponent(entity, LevelComponent::class.java)
            .orElseThrow { IllegalStateException("Level component missing.") }

    lvComp.level = level

    // Invalidate the status points if the entity has a status component.
    statusService.calculateStatusPoints(entity)
  }

  /**
   * Returns the level of the entity. The entity must possess the
   * [LevelComponent] or 1 is returned.
   */
  fun getLevel(entity: Entity): Int {

    return entityService.getComponent(entity, LevelComponent::class.java)
            .map { it.level }
            .orElse(1)
  }

  private fun checkLevelup(entity: Entity, levelComponent: LevelComponent) {

    val neededExp = Math
            .round(Math.pow(levelComponent.level.toDouble(), 3.0) / 10 + 15.0 + levelComponent.level * 1.5).toInt()

    LOG.trace("Entity {} has {}/{} exp for levelup.", entity, neededExp, levelComponent.exp)

    if (levelComponent.exp > neededExp && levelComponent.level < MAX_LEVEL) {
      levelComponent.exp = levelComponent.exp - neededExp
      levelComponent.level = levelComponent.level + 1
      checkLevelup(entity, levelComponent)
    }

    // Finally recalculate the status if all level ups have recursively
    // resolved.
    statusService.calculateStatusPoints(entity)
    entityService.updateComponent(levelComponent)
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

    LOG.trace("Entity {} gains {} exp.", entity, exp)

    if (exp < 0) {
      throw IllegalArgumentException("Exp can not be negative.")
    }

    val levelComp = entityService.getComponent(entity, LevelComponent::class.java)
            .orElseThrow { IllegalArgumentException() }

    levelComp.exp = levelComp.exp + exp
    checkLevelup(entity, levelComp)
  }

  /**
   * The current experience of the entity. Entity must possess
   * [LevelComponent] or 0 will be returned.
   *
   * @param entity
   * The entity to get its experience points.
   * @return The current exp or 0 if the [LevelComponent] is missing.
   */
  fun getExp(entity: Entity): Int {
    return entityService.getComponent(entity, LevelComponent::class.java)
            .map { it.exp }
            .orElse(0)
  }

  companion object {
    private const val MAX_LEVEL = 10
  }
}
