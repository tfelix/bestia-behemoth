package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestiaDAO
import net.bestia.model.findOneOrThrow
import net.bestia.model.bestia.BaseValues
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.MetaDataComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

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
    private val playerBestiaDao: PlayerBestiaDAO,
    private val bestiaDao: BestiaRepository
) {

  /**
   * Trigger the status point calculation. If some preconditions of status
   * calculation have changed recalculate the status for this given entity.
   * The entity must own a [StatusComponent].
   * Calculates and sets the modified status points based on the equipment and
   * or status effects. Each status effect can possibly return a modifier
   * which will then modify the original base status values.
   *
   * @param entity
   * The entity to recalculate the status.
   */
  fun calculateStatusPoints(entity: Entity) {
    LOG.trace("Calculate status points for entity {}.", entity)

    val metaDataComponent = entity.tryGetComponent(MetaDataComponent::class.java)
    val bestiaId = metaDataComponent?.data?.get(MetaDataComponent.MOB_BESTIA_ID) as? Int

    val isPlayer = entity.hasComponent(PlayerComponent::class.java)

    when {
      bestiaId != null -> calculateMobStatus(bestiaId, entity)
      isPlayer -> calculatePlayerStatus(entity)
    }

    calculateModifiedStatusPoints(entity)

    val statusComp = entity.getComponent(StatusComponent::class.java)
    entityService.updateComponent(statusComp)
  }

  private fun calculateMobStatus(bestiaId: Int, entity: Entity) {
    val bestia = bestiaDao.findOneOrThrow(bestiaId)
    calculateUnmodifiedStatusPoints(
        entity = entity,
        bVals = bestia.baseValues
    )
  }

  private fun calculatePlayerStatus(entity: Entity) {
    val playerComp = entity.getComponent(PlayerComponent::class.java)
    val pb = playerBestiaDao.findOneOrThrow(playerComp.playerBestiaId)

    val bVals = pb.baseValues
    val eVals = pb.effortValues
    val iVals = pb.individualValue

    calculateUnmodifiedStatusPoints(entity, bVals, iVals, eVals)
  }

  /**
   * At first this calculates the unmodified, original status points.
   */
  private fun calculateUnmodifiedStatusPoints(
      entity: Entity,
      bVals: BaseValues,
      iVals: BaseValues = BaseValues.nullValues,
      eVals: BaseValues = BaseValues.nullValues
  ) {
    LOG.trace { "Calculate unmodfified status points for entity $entity." }

    val lv = entity.tryGetComponent(LevelComponent::class.java)?.level ?: 1
    val statusComp = entity.getComponent(StatusComponent::class.java)


    val sp = statusComp.originalStatusPoints
    val condVals = statusComp.conditionValues

    val str = (bVals.strength * 2 + iVals.strength + eVals.strength / 4) * lv / 100 + 5
    val vit = (bVals.vitality * 2 + iVals.vitality + eVals.vitality / 4) * lv / 100 + 5
    val intel = (bVals.intelligence * 2 + iVals.intelligence + eVals.intelligence / 4) * lv / 100 + 5
    val will = (bVals.willpower * 2 + iVals.willpower + eVals.willpower / 4) * lv / 100 + 5
    val agi = (bVals.agility * 2 + iVals.agility + eVals.agility / 4) * lv / 100 + 5
    val dex = (bVals.dexterity * 2 + iVals.dexterity + eVals.dexterity / 4) * lv / 100 + 5
    val maxHp = bVals.hp * 2 + iVals.hp + eVals.hp / 4 * lv / 100 + 10 + lv
    val maxMana = (bVals.mana * 2 + iVals.mana + eVals.mana / 4 * lv / 100 + 10 + lv * 2)

    condVals.maxHealth = maxHp
    condVals.maxMana = maxMana

    sp.strength = str
    sp.vitality = vit
    sp.intelligence = intel
    sp.willpower = will
    sp.agility = agi
    sp.dexterity = dex
  }

  /**
   * Currently as there are no status mods and buffs, debuffs etc. we just use the values 1:1 from the
   * unmodified values.
   */
  private fun calculateModifiedStatusPoints(entity: Entity) {
    val statusComp = entity.getComponent(StatusComponent::class.java)
    statusComp.statusPoints.set(statusComp.originalStatusPoints)
  }

  /**
   * Returns the mana value ticked per regeneration step. Note that this value
   * might be smaller then 1. We use this to save the value between the ticks
   * until we have at least 1 mana and can add this to the user status.
   *
   * @param entity The entity.
   * @return The ticked mana value.
   */
  fun getManaTick(entity: Entity): Float {
    val statusComponent = entity.getComponent(StatusComponent::class.java)
    val manaRegenRate = statusComponent.statusBasedValues.manaRegenRate

    // Calc the added value.
    val manaRegen = manaRegenRate / 1000 * REGENERATION_TICK_RATE_MS
    LOG.trace { "Tick Mana $manaRegen for entity $entity." }

    return manaRegen
  }

  /**
   * Returns the health value ticked per regeneration step. Note that this
   * value might be smaller then 1. We use this to save the value between the
   * ticks until we have at least 1 health and can add this to the user
   * status.
   *
   * @param entity The entity.
   * @return The ticked health value.
   */
  fun getHealthTick(entity: Entity): Float {
    val statusComponent = entity.getComponent(StatusComponent::class.java)
    val hpRegenRate = statusComponent.statusBasedValues.hpRegenRate

    val hpRegen = hpRegenRate / 1000 * REGENERATION_TICK_RATE_MS
    LOG.trace { "Tick HP regen $hpRegen for entity $entity." }

    return hpRegenRate
  }

  companion object {
    /**
     * How often the regeneration should tick for each entity.
     */
    const val REGENERATION_TICK_RATE_MS = 8000
  }
}
