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

    val entity = newEntity()
    val components = listOf(
        PositionComponent(
            entityId = entity.id,
            shape = playerBestia.currentPosition
        ),
        VisualComponent(
            entityId = entity.id,
            mesh = playerBestia.origin.mesh
        ),
        EquipComponent(
            entityId = entity.id
        ),
        InventoryComponent(
            entityId = entity.id
        ),
        PlayerComponent(
            entityId = entity.id,
            playerBestiaId = playerBestia.id
        ),
        OwnerComponent(
            entityId = entity.id,
            ownerAccountIds = setOf(playerBestia.owner.id)
        ),
        LevelComponent(
            entityId = entity.id,
            level = playerBestia.level,
            exp = playerBestia.exp
        ),
        originalStatusComponentFactory.buildComponent(entity)
    )
    components.forEach { entity.addComponent(it) }

    LOG.debug { "Build PlayerBestia $entity" }

    return entity
  }
}
