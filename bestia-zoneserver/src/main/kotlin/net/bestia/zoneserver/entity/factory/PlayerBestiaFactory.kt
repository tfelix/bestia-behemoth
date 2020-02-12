package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.*
import net.bestia.zoneserver.status.GeneralOriginalStatusComponentFactory
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
    private val originalStatusComponentFactory: GeneralOriginalStatusComponentFactory,
    idGenerator: IdGenerator
) : EntityFactory(idGenerator) {

  fun build(playerBestiaId: Long): Entity {
    val playerBestia = playerBestiaDao.findOneOrThrow(playerBestiaId)

    val entity = newEntity().apply {
      addComponent(PositionComponent(
          entityId = id,
          shape = playerBestia.currentPosition
      ))
      addComponent(VisualComponent(
          entityId = id,
          mesh = playerBestia.origin.mesh
      ))
      addComponent(EquipComponent(
          entityId = id
      ))
      addComponent(InventoryComponent(
          entityId = id
      ))
      addComponent(PlayerComponent(
          entityId = id,
          ownerAccountId = playerBestia.owner.id,
          playerBestiaId = playerBestia.id
      ))
      addComponent(LevelComponent(
          entityId = id,
          level = playerBestia.level,
          exp = playerBestia.exp
      ))
      addComponent(TagComponent(
          entityId = id,
          tags = setOf(TagComponent.PLAYER)
      ))
      addComponent(originalStatusComponentFactory.buildComponent(this))
    }

    LOG.debug { "Build PlayerBestia $entity" }

    return entity
  }
}
