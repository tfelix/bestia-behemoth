package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.model.dao.BestiaDAO
import net.bestia.model.domain.Bestia
import net.bestia.model.domain.SpriteInfo
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.battle.StatusService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException

import java.util.Objects

private val LOG = KotlinLogging.logger { }

/**
 * Mob factory will create entities which serve as standard mobs for the bestia
 * system.
 *
 * @author Thomas Felix
 */
@Component
internal class MobFactory(
    private val statusService: StatusService,
    private val bestiaDao: BestiaDAO
) : AbstractFactory<MobBlueprint>(MobBlueprint::class.java) {

  override fun performBuild(entity: Entity, blueprint: MobBlueprint) {

    val bestia = bestiaDao.findByDatabaseName(blueprint.mobDbName)
        ?: throw IllegalArgumentException("Could not find bestia in database for blueprint $blueprint.")

    LOG.debug { "Spawning mob entity: $blueprint" }

    entity.addComponent(
        PositionComponent(
            entityId = entity.id,
            shape = blueprint.position
        )
    )
    entity.addComponent(
        VisualComponent(
            entityId = entity.id,
            visual = bestia.spriteInfo
        )
    )

    entity.addComponent(
        LevelComponent(
            entityId = entity.id
        ).apply { this.level = bestia.level }
    )

    entity.addComponent(EquipComponent(entityId = entity.id))
    entity.addComponent(InventoryComponent(entityId = entity.id))
    entity.addComponent(
        StatusComponent(
            entityId = entity.id,
            statusPoints = bestia.statusPoints,
            element = bestia.element
        )
    )

    statusService.calculateStatusPoints(entity)
  }
}
