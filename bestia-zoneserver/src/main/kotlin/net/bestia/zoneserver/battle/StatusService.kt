package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.LevelComponent
import net.bestia.entity.component.PlayerComponent
import net.bestia.entity.component.StatusComponent
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.ConditionValues
import net.bestia.model.domain.StatusPoints
import net.bestia.model.domain.StatusPointsImpl
import net.bestia.model.entity.StatusBasedValues
import net.bestia.model.entity.StatusBasedValuesImpl
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * The service class is responsible for recalculating the status values for a
 * entity. All kind of calculations are considered, this means equipment, status
 * effects and so on.
 *
 * @author Thomas Felix
 */
@Service
class StatusService(
        private val entityService: EntityService,
        private val playerBestiaDao: PlayerBestiaDAO
) {

  /**
   * This method should be used to retrieve status based values for an entity.
   * In case this values can not be retrieved thought the component is
   * available the values will be recalculated.
   */
  fun getStatusBasedValues(entity: Entity): Optional<StatusBasedValues> {
    Objects.requireNonNull(entity)

    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)

    if (!statusComp.isPresent) {
      return Optional.empty()
    }

    if (statusComp.get().statusPoints == null || statusComp.get().statusBasedValues == null) {
      calculateStatusPoints(entity, statusComp.get())
    }

    return Optional.of(statusComp.get().statusBasedValues)
  }

  /**
   * Alias to [.getStatusPoints].
   *
   * @param entityId
   * The entity ID to get the status points for.
   * @return The found status points.
   */
  fun getStatusPoints(entityId: Long): Optional<StatusPoints> {
    val entity = entityService.getEntity(entityId)
    return getStatusPoints(entity)
  }

  /**
   * Returns the status points of an entity which has this component. If the
   * values are not set then they are recalculated. It returns the modified
   * status values (they can be modified by status effects which are added via
   * spells/buffs or via equipment).
   *
   * The entity must posess the [StatusComponent] or an empty optional
   * is returned.
   *
   * @param entity
   * The entity which status points to retrieve.
   * @return The by status effects or equip modified [StatusPoints].
   */
  fun getStatusPoints(entity: Entity): Optional<StatusPoints> {
    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)

    return statusComp.map { it.statusPoints }

  }

  fun getUnmodifiedStatusPoints(entity: Entity): Optional<StatusPoints> {
    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)

    return statusComp.map { it.originalStatusPoints }
  }

  /**
   * Trigger the status point calculation. If some preconditions of status
   * calculation have changed recalculate the status for this given entity.
   * The entity must own a [StatusComponent].
   *
   * @param entity
   * The entity to recalculate the status.
   */
  fun calculateStatusPoints(entity: Entity) {
    Objects.requireNonNull(entity)

    entityService.getComponent(entity, StatusComponent::class.java)
            .ifPresent { statusComp -> calculateStatusPoints(entity, statusComp) }
  }

  /**
   * At first this calculates the unmodified, original status points.
   */
  private fun calculatePlayerUnmodifiedStatusPoints(entity: Entity, statusComp: StatusComponent, level: Int) {
    Objects.requireNonNull(entity)

    LOG.trace("Calculate unmodfified status points for entity {}.", entity)

    val playerComp = entityService.getComponent(entity, PlayerComponent::class.java)
            .orElseThrow { IllegalStateException() }

    val statusPoints = statusComp.originalStatusPoints
    val condValues = statusComp.conditionValues

    val pb = playerBestiaDao.findOneOrThrow(playerComp.playerBestiaId)

    val baseValues = pb.baseValues
    val effortValues = pb.effortValues
    val ivs = pb.individualValue

    val str = (baseValues.attack * 2 + ivs.attack + effortValues.attack / 4) * level / 100 + 5

    val vit = (baseValues.vitality * 2 + ivs.vitality + effortValues.vitality / 4) * level / 100 + 5

    val intel = (baseValues.intelligence * 2 + ivs.intelligence
            + effortValues.intelligence / 4) * level / 100 + 5

    val will = (baseValues.willpower * 2 + ivs.willpower + effortValues.willpower / 4) * level / 100 + 5

    val agi = (baseValues.agility * 2 + ivs.agility + effortValues.agility / 4) * level / 100 + 5

    val dex = (baseValues.dexterity * 2 + ivs.dexterity + effortValues.dexterity / 4) * level / 100 + 5

    val maxHp = baseValues.hp * 2 + ivs.hp + effortValues.hp / 4 * level / 100 + 10 + level

    val maxMana = (baseValues.mana * 2 + ivs.mana + effortValues.mana / 4 * level / 100 + 10
            + level * 2)

    condValues.maxHealth = maxHp
    condValues.maxMana = maxMana

    statusPoints.setStrenght(str)
    statusPoints.vitality = vit
    statusPoints.intelligence = intel
    statusPoints.willpower = will
    statusPoints.agility = agi
    statusPoints.dexterity = dex

    // Update all component values.
    statusComp.originalStatusPoints = statusPoints

    entityService.updateComponent(statusComp)
  }

  /**
   * Calculates and sets the modified status points based on the equipment and
   * or status effects. Each status effect can possibly return a modifier
   * which will then modify the original base status values.
   */
  private fun calculateStatusPoints(entity: Entity, statusComp: StatusComponent) {
    Objects.requireNonNull(entity)

    LOG.trace("Calculate status points for entity {}.", entity)

    // Retrieve the level.
    val level = entityService.getComponent(entity, LevelComponent::class.java)
            .map { it.level }
            .orElse(1)

    // Mob entities should have their status points already be set.
    if (entityService.hasComponent(entity, PlayerComponent::class.java)) {
      calculatePlayerUnmodifiedStatusPoints(entity, statusComp, level)
    }

    // Currently only use status values 1:1.
    val statusPoints = StatusPointsImpl(statusComp.originalStatusPoints)

    statusComp.statusPoints = statusPoints
    statusComp.statusBasedValues = StatusBasedValuesImpl(statusPoints, level)

    entityService.updateComponent(statusComp)
  }

  /**
   * Returns the mana value ticked per regeneration step. Note that this value
   * might be smaller then 1. We use this to save the value between the ticks
   * until we have at least 1 mana and can add this to the user status.
   *
   * @param entityId
   * The ID of the entity.
   * @return The ticked mana value.
   */
  fun getManaTick(entityId: Long): Float {
    val statusComponent = entityService.getComponent(entityId, StatusComponent::class.java)
            .orElseThrow { IllegalArgumentException() }

    val manaRegenRate = statusComponent.statusBasedValues.manaRegenRate

    // Calc the added value.
    val manaRegen = manaRegenRate / 1000 * REGENERATION_TICK_RATE_MS
    LOG.trace("Ticking mana regen {} for entity {}.", manaRegen, entityId)

    return manaRegen
  }

  /**
   * Returns the health value ticked per regeneration step. Note that this
   * value might be smaller then 1. We use this to save the value between the
   * ticks until we have at least 1 health and can add this to the user
   * status.
   *
   * @param entityId
   * The ID of the entity.
   * @return The ticked health value.
   */
  fun getHealthTick(entityId: Long): Float {
    val statusComponent = entityService.getComponent(entityId, StatusComponent::class.java)
            .orElseThrow { IllegalArgumentException() }

    val hpRegenRate = statusComponent.statusBasedValues.hpRegenRate

    // Calc the added value.
    val hpRegen = hpRegenRate / 1000 * REGENERATION_TICK_RATE_MS
    LOG.trace("Ticking hp regen {} for entity {}.", hpRegen, entityId)

    return hpRegen
  }

  /**
   * Returns the status values for the given entity. Alias für
   * [.getStatusPoints].
   *
   * @param entityId
   * The entity id.
   * @return The [ConditionValues] of this entity.
   */
  fun getConditionalValues(entityId: Long): Optional<ConditionValues> {
    val e = entityService.getEntity(entityId) ?: return Optional.empty()

    return getConditionalValues(e)
  }

  /**
   * Returns the status values for the given entity. The values should be
   * retrieved using this method in order to let the status values be
   * recalculated if needed.
   *
   * @param entity
   * The entity.
   * @return The [ConditionValues] of this entity.
   */
  fun getConditionalValues(entity: Entity): Optional<ConditionValues> {

    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)
    return statusComp.map { it.conditionValues }
  }

  /**
   * Saves the status values into the specific component of the given entity.
   * It also makes sure the current mana and health are not exeeding the max
   * hp and max mana from the status points of this entity.
   *
   * @param entity
   * The entity to save the conditional values.
   * @param values
   * The new conditional values.
   */
  fun save(entity: Entity, values: ConditionValues) {

    Objects.requireNonNull(entity)
    Objects.requireNonNull(values)

    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)
            .orElseThrow { IllegalArgumentException() }

    // If values are equal if so dont do anything.
    val curValues = statusComp.conditionValues

    if (curValues == values) {
      return
    }

    curValues.set(values)

    entityService.updateComponent(statusComp)
  }

  /**
   * This is an alias for [.save].
   *
   * @param entityId
   * The entity ID for which to save the status values.
   * @param sval
   * The status values to save for this entity.
   */
  fun save(entityId: Long, sval: ConditionValues) {
    val e = entityService.getEntity(entityId)
    save(e, sval)
  }

  /**
   * Attaches the new set status points to the entity.
   *
   * @param entity
   * The entity to attach the status points to.
   * @param spoint
   * The new status points to save.
   */
  fun save(entity: Entity, spoint: StatusPoints) {

    Objects.requireNonNull(entity)
    Objects.requireNonNull(spoint)

    val statusComp = entityService.getComponent(entity, StatusComponent::class.java)
            .orElseThrow { IllegalArgumentException() }

    if (statusComp.statusPoints == spoint) {
      return
    }

    // Reset the status based values since they need to be recalculated now.
    // Get them via getStatusBasedValues.
    statusComp.statusPoints = spoint
    statusComp.statusBasedValues = null

    entityService.updateComponent(statusComp)
  }

  /**
   * Saves the new status points to the entity. It first resolves the entity.
   * The its an alias to [.save].
   *
   * @param entityId
   * The entity to attach the status points to.
   * @param spoint
   * The new status points to save.
   */
  fun save(entityId: Long, spoint: StatusPoints) {
    val e = entityService.getEntity(entityId)
    save(e, spoint)
  }

  companion object {
    /**
     * How often the regeneration should tick for each entity.
     */
    const val REGENERATION_TICK_RATE_MS = 8000
  }
}
