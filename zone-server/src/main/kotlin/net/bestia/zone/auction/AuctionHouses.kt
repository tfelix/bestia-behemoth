package net.bestia.zone.auction

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.stereotype.Component

/** Which auction house a player stands at. Asked on every request, because a client can name any entity. */
@Component
class AuctionHouses(
  private val worldService: WorldService,
  private val startTownMarkets: StartTownMarkets,
  private val structures: PlayerStructureRegistry,
  private val towns: TownRegistry,
  private val houses: PlayerHouses,
) {

  /** The house a request is made at. */
  sealed interface At {
    val townName: String

    /** A start town's market hall: one shared market with no owner. */
    data class StartTown(override val townName: String) : At

    /**
     * A house a master built. Open while a standing town holds it; [townName] is empty while it is closed.
     */
    data class Player(val house: PlayerHouses.House, override val townName: String) : At {
      val isOpen: Boolean
        get() {
          return townName.isNotEmpty()
        }
    }
  }

  /** The house [houseEntityId] is, or null unless it is an auction house and [actorId] is within reach of it. */
  fun houseInReach(world: World, actorId: EntityId, houseEntityId: EntityId): At? {
    val visual = world.get(houseEntityId, StaticVisual::class) ?: return null
    if (visual.kind != StaticEntityKind.AUCTION_HOUSE) return null

    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    val pose = world.get(houseEntityId, PropPose::class) ?: return null

    if (PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize) > REACH_TILES) {
      return null
    }

    val structureId = world.get(houseEntityId, PlayerStructureIdentity::class)?.structureId
    if (structureId != null) {
      val house = houses.of(structureId) ?: return null
      val position = structures.of(structureId)?.position ?: return null

      return At.Player(house, towns.standingAt(position)?.name.orEmpty())
    }

    val propId = world.get(houseEntityId, WorldObjectIdentity::class)?.propId ?: return null

    return startTownMarkets.townOf(propId)?.let { At.StartTown(it) }
  }

  /** Every town whose house shares the market. All start towns share one today. */
  fun linkedTowns(): List<String> {
    return startTownMarkets.towns()
  }

  companion object {
    /** Tiles from the walls. The client stops two tiles out, so one tile of slack absorbs latency. */
    const val REACH_TILES = 3.0
  }
}
