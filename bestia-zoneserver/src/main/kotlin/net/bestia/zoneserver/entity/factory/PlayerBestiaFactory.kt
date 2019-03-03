package net.bestia.zoneserver.entity.factory

import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.battle.PlayerStatusService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGeneratorService
import net.bestia.zoneserver.entity.component.*

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 *
 * @author Thomas Felix
 */
class PlayerBestiaFactory(
    private val playerBestiaDao: PlayerBestiaRepository,
    private val playerStatusService: PlayerStatusService,
    private val idGenerator: IdGeneratorService
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
          visual = SpriteInfo.mob(playerBestia.origin.sprite)
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

      val statusComponent = playerStatusService.calculateStatusPoints(entity)
      addComponent(statusComponent)
    }

    return entity
  }
}
