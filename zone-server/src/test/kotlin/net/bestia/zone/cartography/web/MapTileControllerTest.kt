package net.bestia.zone.cartography.web

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.Role
import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The tile endpoint, over a real socket, with a real token and a real session.
 *
 * Testing it this way rather than through `MockMvc` is about the parts that only exist on the wire: conditional
 * requests, the two different `Cache-Control` policies, and that uncharted ground is a 404 with no body rather
 * than an empty image.
 *
 * Nothing is mocked. The token is signed here with the same secret `LoginTokenValidator` verifies against, and
 * the session is established through `GameClientMockFactory` the way every scenario does - so this exercises the
 * filter's actual job, which is turning a login token into the master whose charts apply. A mocked validator
 * would have tested the controller and skipped the only interesting thing about the filter.
 *
 * `spring.main.web-application-type` is overridden because `application-test.yml` pins it to `none`; the rest of
 * the suite has no use for a servlet container and this is the one test that does.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.main.web-application-type=servlet",
    // Its own database, because the property above already forces its own Spring context - and two contexts
    // pointed at one *named* in-memory H2 share every row. That is not theoretical: the shared context spawns
    // masters and persists entities, and a second world restoring those rows into its own ECS collides on ids.
    "spring.datasource.url=jdbc:h2:mem:behemoth-maptile"
  ]
)
@ActiveProfiles("no-socket", "test")
class MapTileControllerTest {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var chartService: ChartService

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var zoneConfig: ZoneConfig

  private lateinit var client: GameClientMock
  private var accountId: Long = 0
  private var masterId: Long = 0
  private lateinit var token: String

  @BeforeAll
  fun signIn() {
    accountId = testFixture.account1.account.id
    masterId = testFixture.account1.masterIds.first()

    client = gameClientFactory.getGameClient(accountId = accountId)
    client.connect(masterId)

    token = signedToken(accountId)
  }

  @AfterAll
  fun signOut() {
    client.disconnect()
  }

  @Test
  fun `a request without a token is refused`() {
    assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity(url(6, 0, 0), String::class.java).statusCode)
  }

  @Test
  fun `a token this server did not sign is refused`() {
    val foreign = Jwts.builder()
      .issuer("login")
      .audience().add("zone").and()
      .subject(accountId.toString())
      .claim("role", Role.USER.name)
      .signWith(Keys.hmacShaKeyFor("a-completely-different-secret-of-sufficient-length".toByteArray()))
      .compact()

    val response = rest.exchange(url(6, 0, 0), HttpMethod.GET, signed(foreign), String::class.java)

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
  }

  @Test
  fun `meta says what a client needs to address a tile`() {
    val response = rest.exchange("/map/v1/meta", HttpMethod.GET, signed(), Map::class.java)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = assertNotNull(response.body)
    assertEquals(TileId.TILE_PIXELS, body["tileSize"])
    assertTrue((body["maxLevel"] as Int) >= 9, "a 128 km world should fit one tile at L9 or coarser")
    assertNotNull(body["worldMapVersion"])
  }

  @Test
  fun `ground the master has never charted is a 404, not an empty tile`() {
    // 404 rather than a blank image, so nothing about that ground reaches the client - not even the file size
    // that would hint at what is drawn on it. A level-0 tile is 256 m, so this is genuinely somewhere else.
    val far = TileId.of(0, 110_000.0, 110_000.0)

    val response = rest.exchange(url(far), HttpMethod.GET, signed(), ByteArray::class.java)

    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `a charted tile is a PNG and revalidates on its ETag`() {
    val centre = charted(radiusMetres = 3_000.0)
    val tile = TileId.of(2, centre.first, centre.second)

    val first = rest.exchange(url(tile), HttpMethod.GET, signed(), ByteArray::class.java)

    assertEquals(HttpStatus.OK, first.statusCode)
    val bytes = assertNotNull(first.body)
    assertTrue(isPng(bytes), "the body is not a PNG (${bytes.size} bytes)")

    val etag = assertNotNull(first.headers.eTag)
    val again = rest.exchange(url(tile), HttpMethod.GET, signed(ifNoneMatch = etag), ByteArray::class.java)

    assertEquals(HttpStatus.NOT_MODIFIED, again.statusCode, "unchanged charts should revalidate to 304")
  }

  @Test
  fun `a fully charted tile is public and immutable, a partly charted one is private`() {
    val centre = charted(radiusMetres = 6_000.0)

    // A level-2 tile is a kilometre across, so a 6 km survey buries it and its falloff margin with it.
    val inside = rest.exchange(
      url(TileId.of(2, centre.first, centre.second)), HttpMethod.GET, signed(), ByteArray::class.java
    )
    assertEquals(HttpStatus.OK, inside.statusCode)
    val shared = assertNotNull(inside.headers.cacheControl)
    assertTrue(shared.contains("public"), "a tile everyone sees identically should be public: $shared")
    assertTrue(shared.contains("immutable"), "and immutable: $shared")

    // A level-6 tile is 16 km across, so the same survey covers only part of it and the bytes are this
    // player's alone.
    val across = rest.exchange(
      url(TileId.of(6, centre.first, centre.second)), HttpMethod.GET, signed(), ByteArray::class.java
    )
    assertEquals(HttpStatus.OK, across.statusCode)
    val personal = assertNotNull(across.headers.cacheControl)
    assertTrue(personal.contains("private"), "a masked tile belongs to one player: $personal")
  }

  @Test
  fun `a level above the pyramid is a bad request rather than a rendered void`() {
    assertEquals(
      HttpStatus.BAD_REQUEST,
      rest.exchange(url(20, 0, 0), HttpMethod.GET, signed(), String::class.java).statusCode
    )
  }

  /**
   * Charts a disc on ground nothing else has charted, and answers its centre.
   *
   * Each call moves 20 km east, because the fixture master is shared across the class and two overlapping
   * surveys would make "partly charted" tests depend on which ran first.
   */
  private fun charted(radiusMetres: Double): Pair<Double, Double> {
    val centreX = 20_000.0 + NEXT_SPOT++ * 20_000.0
    val centreY = 20_000.0

    inventoryService.addItem(
      masterRepository.findByIdOrThrow(masterId), ChartService.BLANK_IDENTIFIER, 1
    )
    val result = chartService.mint(masterId, centreX, centreY, radiusMetres)
    assertTrue(result is ChartService.Result.Ok, "could not chart the ground this test is about: $result")

    // The tile service remembers a master's coverage briefly; wait it out rather than reaching in to clear it.
    Thread.sleep(COVERAGE_TTL_SLACK_MILLIS)

    return centreX to centreY
  }

  private fun signedToken(accountId: Long): String = Jwts.builder()
    .issuer("login")
    .audience().add("zone").and()
    .subject(accountId.toString())
    .claim("role", Role.USER.name)
    .signWith(Keys.hmacShaKeyFor(zoneConfig.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8)))
    .compact()

  private fun signed(bearer: String = token, ifNoneMatch: String? = null): HttpEntity<Void> {
    val headers = HttpHeaders()
    headers.set("Authorization", "Bearer $bearer")
    ifNoneMatch?.let { headers.set("If-None-Match", it) }

    return HttpEntity(headers)
  }

  private fun url(tile: TileId) = url(tile.level, tile.tx, tile.ty)

  private fun url(level: Int, tx: Long, ty: Long) = "/map/v1/t/$level/$tx/$ty.png"

  private fun isPng(bytes: ByteArray) =
    bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()

  private companion object {
    /** Keeps each test's survey on its own ground, since the fixture master is shared. */
    var NEXT_SPOT = 0

    /** Comfortably past `MapTileService.COVERAGE_TTL_MILLIS`. */
    const val COVERAGE_TTL_SLACK_MILLIS = 2_500L
  }
}
