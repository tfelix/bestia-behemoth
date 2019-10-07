package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.bestia.BaseValues
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.bestia.StatusValues
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 *
 * @author Thomas Felix
 */
@Component
class PlayerBestiaFactory(
    private val playerBestiaDao: PlayerBestiaRepository,
    private val idGenerator: IdGenerator
) {

  fun build(playerBestiaId: Long): Entity {
    val playerBestia = playerBestiaDao.findOneOrThrow(playerBestiaId)

    val entityId = idGenerator.newId()
    val entity = Entity(entityId)

    entity.apply {
      addComponent(PositionComponent(
          entityId = entity.id,
          shape = playerBestia.currentPosition
      ))
      addComponent(VisualComponent(
          entityId = entity.id,
          mesh = playerBestia.origin.mesh
      ))
      addComponent(EquipComponent(
          entityId = entity.id
      ))
      addComponent(InventoryComponent(
          entityId = entity.id
      ))
      addComponent(PlayerComponent(
          entityId = entity.id,
          ownerAccountId = playerBestia.owner.id,
          playerBestiaId = playerBestia.id
      ))
      addComponent(LevelComponent(
          entityId = entity.id,
          level = playerBestia.level,
          exp = playerBestia.exp
      ))
      addComponent(TagComponent(
          entityId = entity.id,
          tags = setOf(TagComponent.PLAYER)
      ))

      addComponent(buildStatusComponent(entity))
    }

    return entity
  }

  private fun buildStatusComponent(entity: Entity): OriginalStatusComponent {
    LOG.trace("Calculate status points for player entity {}.", entity)
    val playerComp = entity.getComponent(PlayerComponent::class.java)
    val levelComp = entity.getComponent(LevelComponent::class.java)

    val pb = playerBestiaDao.findOneOrThrow(playerComp.playerBestiaId)

    val lv = levelComp.level
    val bVals = pb.baseValues
    val eVals = pb.effortValues
    val iVals = pb.individualValue

    val originalStatus = calculateUnmodifiedStatusValues(lv, bVals, iVals, eVals)

    return OriginalStatusComponent(
        entityId = entity.id,
        statusValues = originalStatus,
        element = pb.origin.element
    )
  }

  /**
   * At first this calculates the unmodified, original status points.
   */
  private fun calculateUnmodifiedStatusValues(
      lv: Int,
      bVals: BaseValues,
      iVals: BaseValues = BaseValues.NULL_VALUES,
      eVals: BaseValues = BaseValues.NULL_VALUES
  ): StatusValues {
    val str = (bVals.strength * 2 + iVals.strength + eVals.strength / 4) * lv / 100 + 5
    val vit = (bVals.vitality * 2 + iVals.vitality + eVals.vitality / 4) * lv / 100 + 5
    val intel = (bVals.intelligence * 2 + iVals.intelligence + eVals.intelligence / 4) * lv / 100 + 5
    val will = (bVals.willpower * 2 + iVals.willpower + eVals.willpower / 4) * lv / 100 + 5
    val agi = (bVals.agility * 2 + iVals.agility + eVals.agility / 4) * lv / 100 + 5
    val dex = (bVals.dexterity * 2 + iVals.dexterity + eVals.dexterity / 4) * lv / 100 + 5

    return BasicStatusValues(
        str,
        vit,
        intel,
        will,
        agi,
        dex
    )
  }
}
