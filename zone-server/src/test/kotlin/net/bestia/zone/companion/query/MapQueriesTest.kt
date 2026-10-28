package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.coverage.SurveyGrid
import net.bestia.zone.cartography.poi.MapPoi
import net.bestia.zone.cartography.poi.MapPoiIndex
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.companion.CompanionRequestDispatcher
import net.bestia.zone.compendium.CompendiumStore
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.master.MasterDeletionService
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.scenarios.ScenarioDataSetup
import net.bestia.zone.session.HttpTicketService
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.awt.Color
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The map as the bus serves it, to the companion app and the game client alike: what is charted, what is fog,
 * what revalidates, and whose ticket is whose. The HTTP side of the same rules is the api-server's MapController.
 *
 * It charts on a master of its own, made here and deleted afterwards, rather than on a fixture master: the shared
 * context's other tests count the fixture masters' inventories. A context of its own would cost a whole generated
 * world, and the suite has no heap to spare for one. The master never enters the world, so it can be deleted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class MapQueriesTest {

  @Autowired
  private lateinit var dispatcher: CompanionRequestDispatcher

  @Autowired
  private lateinit var chartService: ChartService

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var httpTickets: HttpTicketService

  @Autowired
  private lateinit var pois: MapPoiIndex

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPoints: MasterSpawnPointService

  @Autowired
  private lateinit var masterDeletion: MasterDeletionService

  @Autowired
  private lateinit var compendium: CompendiumStore

  private lateinit var client: GameClientMock
  private var accountId: Long = 0
  private var masterId: Long = 0

  @BeforeAll
  fun signIn() {
    accountId = testFixture.account3.account.id
    masterId = masterFactory.create(
      accountId,
      MasterFactory.CreateMasterData(
        name = MASTER_NAME,
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPoints.ensureComputed().first().id.toInt(),
        slot = FREE_SLOT,
      ),
    ).id

    // The session is the fixture master's: the account holds a ticket, and the charting master stays out of the
    // world, where it could not be deleted.
    client = gameClientFactory.getGameClient(accountId = accountId)
    client.connect(testFixture.account3.masterIds.first())
  }

  @AfterAll
  fun signOut() {
    client.disconnect()
    assertIs<MasterDeletionService.Result.Deleted>(masterDeletion.delete(accountId, masterId, MASTER_NAME))
  }

  @Test
  fun `a ticket names its connection's account, and only while the connection lives`() {
    assertEquals(ZoneAnswer.TicketOwner(accountId), ask(ZoneQuery.WhoseTicket(httpTickets.ticketFor(accountId))).answer)
    assertEquals(Status.NOT_FOUND, ask(ZoneQuery.WhoseTicket("11111111-2222-3333-4444-555555555555")).status)

    // On its own account rather than the fixture one, whose session every other test here needs.
    val otherAccountId = testFixture.account2.account.id
    val other = gameClientFactory.getGameClient(accountId = otherAccountId)
    other.connect(testFixture.account2.masterIds.first())
    val ticket = httpTickets.ticketFor(otherAccountId)
    assertEquals(Status.OK, ask(ZoneQuery.WhoseTicket(ticket)).status)

    other.disconnect()

    assertEquals(Status.NOT_FOUND, ask(ZoneQuery.WhoseTicket(ticket)).status)
  }

  @Test
  fun `meta says what a client needs to address a tile`() {
    val meta = assertIs<ZoneAnswer.MapInfo>(ask(ZoneQuery.MapMeta).answer)

    assertEquals(TileId.TILE_PIXELS, meta.tileSize)
    assertEquals(TileId.COARSEST_LEVEL, meta.maxLevel, "a 128 km world is served down to 128 m per pixel and no coarser")
    assertTrue(meta.worldMapVersion.isNotBlank() && meta.metresPerVoxel > 0)
  }

  @Test
  fun `another account's master is not there, however charted`() {
    val stranger = testFixture.account2.account.id
    val centre = charted(radiusMetres = 3_000.0)

    assertEquals(Status.NOT_FOUND, ask(tileQuery(TileId.of(2, centre.first, centre.second)), stranger).status)
  }

  @Test
  fun `ground the master has never charted is not found, not an empty tile`() {
    // So nothing about that ground leaves the zone - not even the size of a file that would hint at what is
    // drawn on it. A level-0 tile is 256 m, so this is genuinely somewhere else.
    assertEquals(Status.NOT_FOUND, ask(tileQuery(TileId.of(0, 110_000.0, 110_000.0))).status)
  }

  @Test
  fun `a charted tile is a PNG and revalidates on its ETag`() {
    val centre = charted(radiusMetres = 3_000.0)
    val tile = TileId.of(2, centre.first, centre.second)

    val first = ask(tileQuery(tile))
    assertEquals(Status.OK, first.status)
    assertTrue(isPng(first.body), "the body is not a PNG (${first.body.size} bytes)")

    val etag = assertIs<ZoneAnswer.Tile>(first.answer).etag
    val again = ask(tileQuery(tile, ifNoneMatch = etag))

    assertEquals(Status.NOT_MODIFIED, again.status, "unchanged charts should revalidate")
    assertEquals(0, again.body.size, "a 304 sends no bytes over the bus")
  }

  @Test
  fun `a fully charted tile is shared, a partly charted one is the master's own`() {
    val centre = charted(radiusMetres = 6_000.0)

    // A level-2 tile is a kilometre across, so a 6 km survey buries it and its falloff margin with it; a
    // level-6 tile is 16 km across, so the same survey covers only part of it.
    assertTrue(assertIs<ZoneAnswer.Tile>(ask(tileQuery(TileId.of(2, centre.first, centre.second))).answer).shared)
    assertTrue(!assertIs<ZoneAnswer.Tile>(ask(tileQuery(TileId.of(6, centre.first, centre.second))).answer).shared)
  }

  @Test
  fun `a tile charted to its own edges but no further is served masked, not refused`() {
    // The ring just inside a chart's boundary: every cell of the tile is charted and the falloff margin around
    // it is not. A level-2 tile is 1024 m, so 800 m buries every cell centre and leaves the margin's corner out.
    val spot = nextSpot().let { (x, y) -> TileId.of(2, x, y) }
    val bounds = spot.bounds
    chartedAt((bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0, radiusMetres = 800.0)

    val masked = ask(tileQuery(spot))
    assertEquals(Status.OK, masked.status)
    assertTrue(isPng(masked.body), "a frontier tile should come back as a masked PNG")

    // Charting the margin changes the mask's key, so the tile is drawn again over the base the first request
    // left in the tile store - the only place that read-back is exercised.
    chartedAt(bounds.minX - 200.0, (bounds.minY + bounds.maxY) / 2.0, radiusMetres = 800.0)

    val redrawn = ask(tileQuery(spot))
    assertEquals(Status.OK, redrawn.status)
    assertTrue(isPng(redrawn.body), "a tile masked over a cached base should still be a PNG")
  }

  @Test
  fun `ground one world width east of a charted tile is not found, not that tile again`() {
    // The survey grid wraps with the world, the imagery does not: without a bounds test the answer would be the
    // player's own chart drawn over blank parchment.
    val centre = charted(radiusMetres = 3_000.0)
    val tile = TileId.of(2, centre.first, centre.second)
    val across = TileId.tilesAcross(meta().worldWidthMetres, tile.level)
    assertEquals(Status.OK, ask(tileQuery(tile)).status, "the tile this test is about is not charted")

    val east = TileId(tile.level, tile.tx + across, tile.ty)
    val west = TileId(tile.level, tile.tx - across, tile.ty)
    for (wrapped in listOf(east, west)) {
      assertEquals(Status.NOT_FOUND, ask(tileQuery(wrapped)).status, "$wrapped is a world width from $tile")
    }
  }

  @Test
  fun `the coarsest level is served whole, with nothing past its last tile and no level above it`() {
    charted(radiusMetres = 3_000.0)
    val coarsest = meta().maxLevel
    assertEquals(Status.OK, ask(tileQuery(TileId(coarsest, 0, 0))).status, "the world should be charted by now")

    val across = TileId.tilesAcross(meta().worldWidthMetres, coarsest)
    val down = TileId.tilesAcross(meta().worldHeightMetres, coarsest)
    for ((tx, ty) in listOf(across to 0L, 0L to down, -1L to 0L, 0L to -1L, across to down)) {
      assertEquals(Status.NOT_FOUND, ask(tileQuery(TileId(coarsest, tx, ty))).status, "L$coarsest/$tx/$ty")
    }

    assertEquals(Status.INVALID, ask(tileQuery(TileId(coarsest + 1, 0, 0))).status, "L${coarsest + 1} is no longer served")
  }

  @Test
  fun `a tile charted a moment ago is not found no longer`() {
    // The first look leaves the master's empty coverage cached; without the eviction on a chart write, the
    // second look would get the first one's answer and the client would write that ground off.
    val spot = nextSpot().let { (x, y) -> TileId.of(3, x, y) }
    val centre = spot.bounds.let { (it.minX + it.maxX) / 2.0 to (it.minY + it.maxY) / 2.0 }
    assertEquals(Status.NOT_FOUND, ask(tileQuery(spot)).status, "this test needs ground nobody has looked at")

    chartedAt(centre.first, centre.second, radiusMetres = 1_000.0)

    val answer = ask(tileQuery(spot))
    assertEquals(Status.OK, answer.status, "ground charted a moment ago is still being refused")
    assertTrue(isPng(answer.body))
  }

  @Test
  fun `services are listed only where they are charted`() {
    val shop = someService(0)
    // 200 m buries the shop's own 64 m cell and little else, so its neighbours in a 2 km tile stay in the fog.
    chartedAt(shop.x, shop.y, radiusMetres = 200.0)

    val listed = servicesIn(TileId.of(3, shop.x, shop.y))

    assertTrue(listed.any { it.id == shop.id }, "the charted service is not listed")
    for (service in listed) {
      val distance = hypot(service.x - shop.x, service.y - shop.y)
      assertTrue(distance < 200.0 + CELL_DIAGONAL, "$service is listed from uncharted ground ($distance m out)")
    }
  }

  @Test
  fun `uncharted services are not found and too coarse a zoom lists none`() {
    assertEquals(Status.NOT_FOUND, ask(servicesQuery(TileId.of(3, 110_000.0, 110_000.0))).status)

    val shop = someService(1)
    chartedAt(shop.x, shop.y, radiusMetres = 1_000.0)

    assertEquals(emptyList(), servicesIn(TileId.of(4, shop.x, shop.y)), "L4 has no room for a glyph per shop")
  }

  @Test
  fun `services revalidate until something in the world changes`() {
    val shop = someService(2)
    chartedAt(shop.x, shop.y, radiusMetres = 1_000.0)
    val tile = TileId.of(2, shop.x, shop.y)

    val etag = assertIs<ZoneAnswer.Services>(ask(servicesQuery(tile)).answer).etag
    assertEquals(Status.NOT_MODIFIED, ask(servicesQuery(tile, ifNoneMatch = etag)).status)

    // A ward stone raised inside the tile and then broken: each is a change every map covering it must see.
    val stone = Long.MAX_VALUE - 7
    pois.onWardStoneRaised(stone, shop.x, shop.y)
    assertEquals(Status.OK, ask(servicesQuery(tile, ifNoneMatch = etag)).status, "a stone was raised, the ETag held")
    assertTrue(servicesIn(tile).any { it.id == stone && it.kind == "WARD_STONE" })

    pois.onWardStoneDestroyed(stone)
    assertTrue(servicesIn(tile).none { it.id == stone }, "a broken stone is still listed")
  }

  @Test
  fun `a species' habitat is shown only once the master has seen it, and only where it is charted`() {
    // A row of its own: another spot on the strip would push the last test's spot off the world's east edge.
    val centre = chartedAt(40_000.0, 32_000.0, radiusMetres = 3_000.0)
    val tile = TileId.of(2, centre.first, centre.second)
    assertEquals(Status.NOT_FOUND, ask(habitatQuery(tile)).status, "an unseen species gives its habitat away")

    compendium.recordSeen(masterId, BLOB)

    val first = ask(habitatQuery(tile))
    assertEquals(Status.OK, first.status)
    assertTrue(isPng(first.body), "the body is not a PNG (${first.body.size} bytes)")
    val etag = assertIs<ZoneAnswer.Tile>(first.answer).etag
    assertEquals(Status.NOT_MODIFIED, ask(habitatQuery(tile, ifNoneMatch = etag)).status)
    assertEquals(Status.NOT_FOUND, ask(habitatQuery(TileId.of(0, 110_000.0, 110_000.0))).status, "uncharted ground")
  }

  @Test
  fun `a place is found by part of its name only once it is charted`() {
    val town = someService(3)
    val name = town.settlement ?: error("a service with a business names its settlement")

    assertTrue(found(name).none { it.name == name }, "$name was found before anyone charted it")

    chartedAt(town.x, town.y, radiusMetres = 500.0)

    assertEquals(name, found(name.lowercase()).first().name)
    assertTrue(found(name.substring(0, 3)).any { it.name == name }, "a prefix of $name finds it")
    assertTrue(found(name.substring(0, 1)).isEmpty(), "one letter matches half the world")
  }

  @Test
  fun `a charted town lists its standing services, and nobody else's master sees it`() {
    val shop = someService(4)
    val name = shop.settlement ?: error("a service with a business names its settlement")
    chartedAt(shop.x, shop.y, radiusMetres = 1_500.0)
    val placeId = found(name).first { it.name == name }.id

    val detail = assertIs<ZoneAnswer.PlaceDetail>(ask(ZoneQuery.MapPlace(masterId, placeId)).answer)

    assertEquals(name, detail.place.name)
    assertTrue(detail.services.isNotEmpty(), "$name has a staffed service")
    assertTrue(detail.services.all { it.settlement == name })
    assertTrue(detail.region != null)
    assertEquals(Status.NOT_FOUND, ask(ZoneQuery.MapPlace(masterId, placeId), asker = 999L).status)
  }

  @Test
  fun `a level above the pyramid is invalid rather than a rendered void`() {
    assertEquals(Status.INVALID, ask(tileQuery(TileId(20, 0, 0))).status)
  }

  /** Charts a disc on ground nothing else has charted, 20 km east of the last, and answers its centre. */
  private fun charted(radiusMetres: Double): Pair<Double, Double> {
    val (x, y) = nextSpot()
    return chartedAt(x, y, radiusMetres)
  }

  /**
   * Ground nothing else has charted: 20 km apart along y = 20 km, then along y = 44 km once that row of the 128 km
   * world is used up. Past the world there is no ground to chart, and a test that asks about it gets a 404 that
   * says nothing about the code under test.
   */
  private fun nextSpot(): Pair<Double, Double> {
    val n = NEXT_SPOT++
    return (20_000.0 + n % SPOTS_PER_ROW * 20_000.0) to (if (n < SPOTS_PER_ROW) 20_000.0 else 44_000.0)
  }

  private fun chartedAt(centreX: Double, centreY: Double, radiusMetres: Double): Pair<Double, Double> {
    inventoryService.addItem(masterId, ChartService.BLANK_IDENTIFIER, 1)
    val result = chartService.mint(masterId, centreX, centreY, radiusMetres)
    assertTrue(result is ChartService.Result.Ok, "could not chart the ground this test is about: $result")
    return centreX to centreY
  }

  /** A staffed service in the [nth] settlement north of the strip the other tests chart along. */
  private fun someService(nth: Int): MapPoi {
    val meta = meta()
    val towns = pois.liveIn(Aabb(0.0, CHARTED_STRIP_NORTH, meta.worldWidthMetres, meta.worldHeightMetres))
      .filter { it.business != null }
      .distinctBy { it.settlement }

    return towns.getOrNull(nth) ?: error("the test world has fewer than ${nth + 1} towns with a service")
  }

  private fun found(text: String): List<ZoneAnswer.Places.Place> {
    val reply = ask(ZoneQuery.MapFind(masterId, text))
    assertEquals(Status.OK, reply.status, "find '$text'")
    return assertIs<ZoneAnswer.Found>(reply.answer).places
  }

  private fun servicesIn(tile: TileId): List<ZoneAnswer.Services.Service> {
    val reply = ask(servicesQuery(tile))
    assertEquals(Status.OK, reply.status, "services of $tile")
    return assertIs<ZoneAnswer.Services>(reply.answer).services
  }

  private fun meta(): ZoneAnswer.MapInfo {
    return assertIs<ZoneAnswer.MapInfo>(ask(ZoneQuery.MapMeta).answer)
  }

  private fun tileQuery(tile: TileId, ifNoneMatch: String? = null): ZoneQuery {
    return ZoneQuery.MapTile(masterId, tile.level, tile.tx, tile.ty, ifNoneMatch)
  }

  private fun habitatQuery(tile: TileId, ifNoneMatch: String? = null): ZoneQuery {
    return ZoneQuery.HabitatTile(masterId, BLOB, tile.level, tile.tx, tile.ty, ifNoneMatch)
  }

  private fun servicesQuery(tile: TileId, ifNoneMatch: String? = null): ZoneQuery {
    return ZoneQuery.MapServices(masterId, tile.level, tile.tx, tile.ty, ifNoneMatch)
  }

  private fun ask(query: ZoneQuery, asker: Long = accountId): BusReply {
    return dispatcher.handle(BusRequest("c", "r", Long.MAX_VALUE, asker, idempotencyKey = null, query = query))
  }

  private fun isPng(bytes: ByteArray): Boolean {
    return bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
  }

  private companion object {
    /** Keeps each test's survey on its own ground, since the class charts on one master throughout. */
    var NEXT_SPOT = 0

    const val SPOTS_PER_ROW = 6

    const val MASTER_NAME = "cartographer"
    const val BLOB = 1L

    /** The fixture master holds slot 0; ChartServiceTest uses slot 1 too, one master at a time. */
    const val FREE_SLOT = 1

    /** Every other test charts at y = 20 km or 44 km, at most 6 km across. */
    const val CHARTED_STRIP_NORTH = 52_000.0

    /** How far past a disc's edge a charted 64 m cell can still reach. */
    val CELL_DIAGONAL = SurveyGrid.CELL_METRES * sqrt(2.0)
  }
}
