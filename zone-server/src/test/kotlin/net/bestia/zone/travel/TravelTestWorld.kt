package net.bestia.zone.travel

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.GroundTrample
import net.bestia.zone.movement.ecs.MoveSystem
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.local.LocalPathfindingService
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaySearches
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.travel.knowledge.RouteKnowledge
import net.bestia.zone.travel.knowledge.RouteSense
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.vicinity.VicinityIndex
import net.bestia.zone.world.vicinity.VicinitySystem
import org.springframework.context.ApplicationEventPublisher

/** One world over [walkQuery] and [macroGraph], with travel, walking and route sight wired as in the game. */
class TravelTestWorld(
  walkQuery: LocalWalkQuery,
  macroGraph: MacroGraphService,
  travelConfig: TravelConfig,
  knowledge: RouteKnowledge,
  onEnded: (JourneyEndedEvent) -> Unit,
  water: WaterSource = WaterSource.NONE,
) {

  private val grid = RegionGrid(water)

  private val navigationConfig = NavigationConfig()
  private val navigation = NavigationService(
    macroGraph,
    LocalPathfindingService(walkQuery, navigationConfig),
    MovementProfileRegistry().apply { load() },
    navigationConfig,
  )
  private val budget = TravelBudget(travelConfig)
  private val travel = TravelSystem(
    RoutePlanner(navigation, knowledge, navigationConfig, WaySearches(grid, pool = null)),
    LegRefiner(navigation, budget, travelConfig, navigationConfig),
    ProgressWatchdog(knowledge, travelConfig),
    budget,
    ApplicationEventPublisher { onEnded(it as JourneyEndedEvent) },
  )
  private val chunkService: ChunkService = mockk {
    every { isReady } returns true
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { normalise(any()) } answers { firstArg() }
  }
  private val vicinityIndex = VicinityIndex()
  private val vicinity = VicinitySystem(chunkService, vicinityIndex, listOf(RouteSense(macroGraph, vicinityIndex, knowledge)))

  val world = testWorld(systems = listOf(travel, MoveSystem({ 0L }, GroundTrample.NONE, walkQuery)))
  val journeys = JourneyService(travelConfig, AttackCancelService(), grid)

  fun bestiaAt(masterId: Long, position: Vec3L): EntityId {
    return world.createEntity { id ->
      world.add(id, Position.fromVec3(position))
      world.add(id, Speed())
      world.add(id, OwnedBestia(masterId = masterId, playerBestiaId = id))
      // As in the game: MoveSystem re-checks a player's every step against the ground.
      world.add(id, Account(masterId))
    }
  }

  fun tick() {
    world.tick(DT)
    vicinity.update(world, DT)
  }

  fun positionOf(id: EntityId): Vec3L {
    return world.getOrThrow(id, Position::class).toVec3L()
  }

  companion object {
    const val DT = 0.05f

    fun onOpenGround(
      config: TravelConfig,
      knowledge: RouteKnowledge,
      onEnded: (JourneyEndedEvent) -> Unit,
      walkable: (Vec3L) -> Boolean = { true },
      water: WaterSource = WaterSource.NONE,
    ): TravelTestWorld {
      val ground = TestNavigation.flatGround(walkable)
      val noGraph = MacroGraphService(TestNavigation.noMacroGraph(), ground, NavigationConfig())
      return TravelTestWorld(ground, noGraph, config, knowledge, onEnded, water)
    }
  }
}
