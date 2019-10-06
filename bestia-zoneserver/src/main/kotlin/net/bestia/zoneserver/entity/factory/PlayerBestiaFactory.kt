package net.bestia.zoneserver.entity.factory

import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.battle.PlayerStatusService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Component

/**
 * The factory is used to create player entities which can be controlled via a
 * player.
 *
 * @author Thomas Felix
 */
@Component
class PlayerBestiaFactory(
    private val playerBestiaDao: PlayerBestiaRepository,
    private val playerStatusService: PlayerStatusService,
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

      val statusComponent = playerStatusService.calculateStatusPoints(entity)
      addComponent(statusComponent)
    }

    return entity
  }
}
