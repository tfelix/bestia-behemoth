package net.bestia.zoneserver.entity.factory

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import net.bestia.model.dao.BestiaDAO
import net.bestia.model.domain.Bestia
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.battle.StatusService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.util.Objects

/**
 * Mob factory will create entities which serve as standard mobs for the bestia
 * system.
 *
 * @author Thomas Felix
 */
class MobFactory(
    entityFactory: EntityFactory, statusService: StatusService, bestiaDao: BestiaDAO
) : EntityBuilder {

  override fun getComponents(): Set<Component> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  init {

    this.entityFactory = Objects.requireNonNull(entityFactory)
    this.statusService = Objects.requireNonNull(statusService)
    this.bestiaDao = Objects.requireNonNull(bestiaDao)
  }

  fun build(moDbName: String, x: Long, y: Long): Entity? {

    val bestia = bestiaDao.findByDatabaseName(moDbName)

    if (bestia == null) {
      LOG.warn("Database does not contain mob bestia: {}", moDbName)
      return null
    }

    LOG.debug("Spawning mob {} ({},{}).", moDbName, x, y)

    val posSetter = PositionComponentSetter(Point(x, y))
    val visSetter = VisibleComponentSetter(bestia.spriteInfo)
    val levelSetter = LevelComponentSetter(bestia.level, 0)
    val tagSetter = TagComponentSetter(Tag.PERSIST)

    val mob = entityFactory.buildEntity(mobBlueprint, posSetter, visSetter, levelSetter, tagSetter)

    // Calculate the status points now.
    statusService.calculateStatusPoints(mob)

    return mob
  }

  companion object {

    private val LOG = LoggerFactory.getLogger(MobFactory::class.java)

    private val mobBlueprint: Blueprint

    init {
      val builder = Blueprint.Builder()
      builder.addComponent(VisualComponent::class.java)
          .addComponent(EquipComponent::class.java)
          .addComponent(InventoryComponent::class.java)
          .addComponent(PositionComponent::class.java)
          .addComponent(LevelComponent::class.java)
          .addComponent(TagComponent::class.java)
          .addComponent(StatusComponent::class.java)

      mobBlueprint = builder.build()
    }
  }
}
