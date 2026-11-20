package net.bestia.zone.stronghold

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.prop.ecs.construction.SiteCompletion
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** A finished Empyrite site stops being a structure and becomes the guild's Empyrite. */
@Component
class EmpyriteSiteCompletion(
  private val strongholds: StrongholdRegistry,
  private val structures: PlayerStructureRegistry,
  private val empyrites: Empyrites,
  private val worldService: WorldService,
) : SiteCompletion {

  override val kind = StaticEntityKind.EMPYRITE_SITE

  override fun complete(world: World, siteEntityId: EntityId, site: ConstructionSite) {
    world.destroy(siteEntityId)
    structures.of(site.structureId)?.let { entry ->
      val chunkSize = worldService.config.chunkSize.toLong()
      val chunkX = Math.floorDiv(entry.position.x, chunkSize).toInt()
      val chunkY = Math.floorDiv(entry.position.y, chunkSize).toInt()
      structures.remove(entry.id, chunkX, chunkY)
    }

    strongholds.all().firstOrNull { it.siteStructureId == site.structureId }?.let { empyrites.stand(world, it) }
  }
}
