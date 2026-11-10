package net.bestia.zone.settlement

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.ecs.construction.ConstructionSite
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.settlement.SettlementPlannedSMSG
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.settlement.persistence.PlayerSettlementRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TownServiceTest {

  private val accountId = 7L
  private val masterId = 3L
  private val signAt = Vec3L(1000, 1000, 10)
  private val siteStructureId = 42L

  private lateinit var world: EcsWorld
  private lateinit var registry: TownRegistry
  private lateinit var structureRegistry: PlayerStructureRegistry
  private lateinit var messages: OutMessageProcessor
  private lateinit var events: ApplicationEventPublisher
  private lateinit var service: TownService
  private var now = Instant.parse("2026-11-10T12:00:00Z")
  private var structures = mutableListOf<StructureEntry>()

  @BeforeEach
  fun setUp() {
    world = testWorld()

    val worldService = mockk<WorldService> {
      every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    }
    val repository = mockk<PlayerSettlementRepository>(relaxed = true)
    every { repository.save(any()) } answers { firstArg() }
    registry = TownRegistry(repository, mockk<AsyncJobExecutor>(relaxed = true), worldService)

    structureRegistry = mockk(relaxed = true)
    every { structureRegistry.all() } answers { structures }
    every { structureRegistry.of(any()) } answers { structures.firstOrNull { it.id == firstArg<Long>() } }

    val playerStructures = mockk<PlayerStructureService>()
    every { playerStructures.beginConstruction(any(), StaticEntityKind.CITY_SIGN, any(), any(), any(), any(), any()) } answers {
      world.createEntity { id -> add(id, PlayerStructureIdentity(siteStructureId, accountId)) }
    }

    val spot = mockk<FoundingSpot> { every { isFree(any()) } returns true }
    messages = mockk(relaxed = true)
    events = mockk(relaxed = true)

    service = TownService(
      towns = registry,
      structures = playerStructures,
      structureRegistry = structureRegistry,
      foundingSpot = spot,
      aoi = mockk(relaxed = true),
      outMessageProcessor = messages,
      events = events,
      skills = mockk(relaxed = true),
      worldService = worldService,
      clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
      },
    )
  }

  @Test
  fun `a name that is too short is refused`() {
    service.plan(accountId, masterId, "ab")

    verify { messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.SETTLEMENT_NAME_INVALID)) }
  }

  @Test
  fun `founding needs a planned name first`() {
    val founder = spawnFounder()

    assertEquals(OpError.REQUEST_REFUSED, service.foundingRefusal(world, founder))
  }

  @Test
  fun `the cast plants a sign site under the planned name`() {
    val founder = spawnFounder()
    service.plan(accountId, masterId, "Oakhold")

    assertTrue(service.found(world, founder))

    val town = registry.heldBy(masterId)
    assertNotNull(town)
    assertEquals("Oakhold", town.name)
    assertEquals(PlayerSettlement.State.SITE, town.state)
    verify { messages.sendToPlayer(accountId, SettlementPlannedSMSG("Oakhold")) }
  }

  @Test
  fun `a master holding a town cannot found a second one`() {
    val founder = spawnFounder()
    service.plan(accountId, masterId, "Oakhold")
    service.found(world, founder)

    service.plan(accountId, masterId, "Elmhold")

    verify { messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.SETTLEMENT_ALREADY_HELD)) }
  }

  @Test
  fun `a finished sign site raises the sign and the town stands`() {
    val sign = foundAndFinish()

    val town = registry.heldBy(masterId)!!
    assertEquals(PlayerSettlement.State.STANDING, town.state)
    assertEquals(town.id, world.get(sign, CitySign::class)?.townId)
    assertEquals(TownGrowth.BASE_SIGN_HP, world.get(sign, Health::class)?.max)
  }

  @Test
  fun `a station in the sphere hardens the sign but keeps its damage`() {
    val sign = foundAndFinish()
    world.get(sign, Health::class)!!.current = 1_500
    structures.add(structure(StaticEntityKind.WORKBENCH, Vec3L(signAt.x + 100, signAt.y, signAt.z)))

    service.regrow(world)

    val town = registry.heldBy(masterId)!!
    assertEquals(175.0, town.size.radiusMetres)
    assertEquals(2_500, world.get(sign, Health::class)?.max)
    assertEquals(2_000, world.get(sign, Health::class)?.current)
  }

  @Test
  fun `a destroyed sign ends the town and locks the spot for 48 h`() {
    foundAndFinish()
    val townId = registry.heldBy(masterId)!!.id

    service.fall(townId)

    assertEquals(PlayerSettlement.State.FALLEN, registry.of(townId)?.state)
    assertNull(registry.heldBy(masterId), "a fallen town is nobody's")
    verify { events.publishEvent(any<TownFellEvent>()) }

    now = now.plusSeconds(47 * 3600)
    service.expire(world)
    assertNotNull(registry.of(townId))

    now = now.plusSeconds(2 * 3600)
    service.expire(world)
    assertNull(registry.of(townId))
  }

  @Test
  fun `an auction house in the sphere keeps the founder from tearing the sign down`() {
    val sign = foundAndFinish()
    val founder = spawnFounder()
    structures.add(structure(StaticEntityKind.AUCTION_HOUSE, Vec3L(signAt.x + 20, signAt.y, signAt.z)))

    assertEquals(OpError.SETTLEMENT_NOT_EMPTY, service.tearDown(world, founder, sign))

    structures.clear()
    assertNull(service.tearDown(world, founder, sign))
    assertNull(registry.heldBy(masterId))
  }

  @Test
  fun `a town takes one post office and nothing outside a town does`() {
    foundAndFinish()

    assertEquals(OpError.SETTLEMENT_NEEDS_TOWN, service.buildingRefusal(Vec3L(9000, 9000, 10), StaticEntityKind.POST_OFFICE))
    assertNull(service.buildingRefusal(signAt, StaticEntityKind.POST_OFFICE))

    structures.add(structure(StaticEntityKind.POST_OFFICE, signAt, buildSeconds = 1800f))
    assertEquals(OpError.SETTLEMENT_ALREADY_HAS_ONE, service.buildingRefusal(signAt, StaticEntityKind.POST_OFFICE))
  }

  @Test
  fun `a sign site left unworked for a day is given up`() {
    val founder = spawnFounder()
    service.plan(accountId, masterId, "Oakhold")
    service.found(world, founder)
    structures.add(structure(StaticEntityKind.CITY_SIGN, signAt, buildSeconds = 600f).copy(id = siteStructureId))
    val site = world.createEntity { id ->
      add(id, ConstructionSite(StaticEntityKind.CITY_SIGN, masterId, siteStructureId, 0f, 600f))
    }

    now = now.plusSeconds(25 * 3600)
    service.expire(world)

    assertNull(registry.heldBy(masterId))
    world.tick(0f)
    assertTrue(!world.isAlive(site))
  }

  private fun foundAndFinish(): EntityId {
    val founder = spawnFounder()
    service.plan(accountId, masterId, "Oakhold")
    service.found(world, founder)

    val site = world.createEntity { id ->
      add(id, Position.fromVec3(signAt))
      add(id, ConstructionSite(StaticEntityKind.CITY_SIGN, masterId, siteStructureId, 0f, 600f))
    }
    service.complete(world, site, world.get(site, ConstructionSite::class)!!)

    var sign: EntityId? = null
    world.each(CitySign::class) { id, _ -> sign = id }

    return sign!!
  }

  private fun spawnFounder(): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(signAt))
      add(id, Master(masterId, "Ada"))
      add(id, Account(accountId))
    }
  }

  private fun structure(kind: StaticEntityKind, at: Vec3L, buildSeconds: Float = 0f): StructureEntry {
    return StructureEntry(structures.size + 100L, kind, 9L, 9L, at, 0f, buildSeconds, buildSeconds)
  }
}
