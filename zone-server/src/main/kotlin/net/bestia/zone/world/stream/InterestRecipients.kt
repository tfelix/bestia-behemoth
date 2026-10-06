package net.bestia.zone.world.stream

import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.aoi.EntityAudience
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.Recipients
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

@Component
class InterestRecipients(
  private val entityAudience: EntityAudience,
  private val playerAOIService: ActivePlayerAOIService,
  private val interestRange: InterestRange,
) : Recipients {

  override fun observersOf(world: World, entityId: EntityId): Set<AccountId> {
    return entityAudience.of(world, entityId)
  }

  override fun inRangeOf(pos: Vec3L): Set<AccountId> {
    return playerAOIService.queryEntitiesInCube(pos, interestRange.cubeEdge)
  }
}
