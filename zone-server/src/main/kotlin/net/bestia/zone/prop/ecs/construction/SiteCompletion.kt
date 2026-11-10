package net.bestia.zone.prop.ecs.construction

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.util.EntityId

/**
 * What a finished site of [kind] becomes, for a kind that is not a static prop once it stands. Every other kind
 * goes to `PlayerStructureService.completeConstruction`.
 */
interface SiteCompletion {

  val kind: StaticEntityKind

  fun complete(world: World, siteEntityId: EntityId, site: ConstructionSite)
}
