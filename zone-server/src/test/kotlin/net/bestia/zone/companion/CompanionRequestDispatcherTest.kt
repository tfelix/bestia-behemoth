package net.bestia.zone.companion

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.profile.IdleStance
import net.bestia.zone.companion.query.NotebookCommands
import net.bestia.zone.compendium.CompendiumStore
import net.bestia.zone.travel.RoutesCollection
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The companion app's questions, answered by the zone from the scenario fixture: account 1 holds two masters and
 * three bestias, account 2 one master.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class CompanionRequestDispatcherTest {

  @Autowired
  private lateinit var dispatcher: CompanionRequestDispatcher

  @Autowired
  private lateinit var masters: MasterRepository

  @Autowired
  private lateinit var playerBestias: PlayerBestiaRepository

  @Autowired
  private lateinit var compendium: CompendiumStore

  @Test
  fun `an account sees its own masters and nobody else's`() {
    val answer = ask(1L, ZoneQuery.ListMasters).answer

    assertIs<ZoneAnswer.Masters>(answer)
    assertEquals(masters.findAllByAccountIdOrderBySlot(1L).map { it.id }, answer.masters.map { it.id })
  }

  @Test
  fun `another account's master is reported as not there`() {
    val foreign = masters.findAllByAccountIdOrderBySlot(2L).first()

    for (query in listOf(
      ZoneQuery.MasterSheet(foreign.id),
      ZoneQuery.BestiaRoster(foreign.id),
      ZoneQuery.Inventory(foreign.id),
      ZoneQuery.PartyRoster(foreign.id),
      ZoneQuery.Compendium(foreign.id),
      ZoneQuery.MapTile(foreign.id, level = 0, x = 0, y = 0, ifNoneMatch = null),
    )) {
      assertEquals(Status.NOT_FOUND, ask(1L, query).status, "for $query")
    }
  }

  @Test
  fun `a master's sheet, bestias and bags are read for its owner`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()

    val sheet = ask(1L, ZoneQuery.MasterSheet(master.id)).answer
    val roster = ask(1L, ZoneQuery.BestiaRoster(master.id)).answer
    val bag = ask(1L, ZoneQuery.Inventory(master.id)).answer

    assertIs<ZoneAnswer.Sheet>(sheet)
    assertEquals(master.name, sheet.name)
    assertIs<ZoneAnswer.Roster>(roster)
    assertEquals(playerBestias.findAllByMasterId(master.id).size, roster.bestias.size)
    assertTrue(roster.bestias.all { it.metresPerSecond > 0.0 })
    assertIs<ZoneAnswer.Bag>(bag)
    assertEquals(listOf("MASTER") + roster.bestias.map { "BESTIA" }, bag.holders.map { it.kind })
  }

  @Test
  fun `the catalogue lists items and species under a stable version`() {
    val first = ask(1L, ZoneQuery.Catalog).answer
    val second = ask(2L, ZoneQuery.Catalog).answer

    assertIs<ZoneAnswer.CatalogData>(first)
    assertTrue(first.items.isNotEmpty() && first.bestias.isNotEmpty())
    assertEquals(first.version, (second as ZoneAnswer.CatalogData).version)
  }

  @Test
  fun `a standing order is stored clamped, and refused for an unknown stance or a stranger`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()
    val bestia = playerBestias.findAllByMasterId(master.id).first()
    val before = bestia.aiConfig

    try {
      val set = ask(1L, ZoneQuery.SetStance(master.id, bestia.id, "hold", aggression = 500))
      assertEquals(ZoneAnswer.Stance(bestia.id, IdleStance.HOLD.name, 100), set.answer)
      assertEquals(IdleStance.HOLD, playerBestias.findByIdOrNull(bestia.id)!!.aiConfig.stance)

      assertEquals(Status.INVALID, ask(1L, ZoneQuery.SetStance(master.id, bestia.id, "DANCE", 10)).status)
      assertEquals(Status.NOT_FOUND, ask(2L, ZoneQuery.SetStance(master.id, bestia.id, "HOLD", 10)).status)
    } finally {
      ask(1L, ZoneQuery.SetStance(master.id, bestia.id, before.stance.name, before.aggression))
    }
  }

  @Test
  fun `a master keeps, renames and forgets routes in the notebook, and a stranger touches none`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()
    val json = """{"name":"","waypoints":[{"east":100.0,"north":200.0},{"east":900.0,"north":950.0}]}"""

    val saved = assertIs<ZoneAnswer.NotebookWritten>(ask(1L, ZoneQuery.NotebookPut(master.id, ROUTES, null, json, null)).answer)
    assertTrue(saved.json!!.contains("\"customName\":false"), "an unnamed route is shown with its made name: ${saved.json}")

    try {
      val renamed = assertIs<ZoneAnswer.NotebookWritten>(
        ask(1L, ZoneQuery.NotebookPut(master.id, ROUTES, saved.key, json.replace("\"name\":\"\"", "\"name\":\"Ore run\""), saved.revision)).answer
      )
      assertEquals(saved.revision + 1, renamed.revision, "each write takes the next revision")
      assertTrue(renamed.json!!.contains("\"name\":\"Ore run\""))

      val stale = ask(1L, ZoneQuery.NotebookPut(master.id, ROUTES, saved.key, json, saved.revision))
      assertEquals(Status.INVALID, stale.status, "a write against the revision before the rename")
      assertEquals(NotebookCommands.STALE, stale.detail)

      assertEquals(Status.NOT_FOUND, ask(2L, ZoneQuery.NotebookPut(master.id, ROUTES, saved.key, json, null)).status)
      assertEquals(Status.NOT_FOUND, ask(2L, ZoneQuery.NotebookList(master.id, ROUTES)).status)
      assertEquals(Status.INVALID, ask(1L, ZoneQuery.NotebookPut(master.id, ROUTES, null, """{"waypoints":[]}""", null)).status)
    } finally {
      assertEquals(Status.OK, ask(1L, ZoneQuery.NotebookDelete(master.id, ROUTES, saved.key, null)).status)
      val left = assertIs<ZoneAnswer.Notebook>(ask(1L, ZoneQuery.NotebookList(master.id, ROUTES)).answer)
      assertTrue(left.entries.none { it.key == saved.key })
    }
  }

  @Test
  fun `a bestia is in the world with nobody playing, is sent off and stopped, and a stranger's never`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()
    val bestia = playerBestias.findAllByMasterId(master.id).first()
    val order = ZoneQuery.SendOnJourney(master.id, bestia.id, listOf(MapPoint(10.0, 10.0)))

    assertEquals(Status.NOT_FOUND, ask(2L, order).status)

    val roster = assertIs<ZoneAnswer.Roster>(ask(1L, ZoneQuery.BestiaRoster(master.id)).answer)
    assertTrue(roster.bestias.single { it.id == bestia.id }.inWorld, "spawned at boot, its master never selected")

    try {
      assertEquals(ZoneAnswer.Done, ask(1L, order).answer)
    } finally {
      assertEquals(ZoneAnswer.Done, ask(1L, ZoneQuery.StopJourney(master.id, bestia.id)).answer)
    }
  }

  @Test
  fun `the compendium lists what the master has seen, and the catalogue the habitat`() {
    // A master of account 2 owns no bestias, so nothing else in the shared context writes to its book.
    val master = masters.findAllByAccountIdOrderBySlot(2L).first()
    try {
      compendium.recordSeen(master.id, BLOB)

      val book = ask(2L, ZoneQuery.Compendium(master.id)).answer
      assertIs<ZoneAnswer.CompendiumData>(book)
      assertEquals(listOf(BLOB), book.entries.map { it.speciesId })
      assertEquals(false, book.entries.single().caught)

      val catalog = assertIs<ZoneAnswer.CatalogData>(ask(2L, ZoneQuery.Catalog).answer)
      val blob = catalog.bestias.single { it.id == BLOB }
      assertTrue("GRASSLAND" in blob.habitat && blob.inCompendium)
    } finally {
      compendium.deleteAllOf(master.id)
    }
  }

  @Test
  fun `the map meta describes the pyramid`() {
    val meta = ask(1L, ZoneQuery.MapMeta).answer

    assertIs<ZoneAnswer.MapInfo>(meta)
    assertTrue(meta.maxLevel >= meta.minLevel)
  }

  private fun ask(accountId: Long, query: ZoneQuery): BusReply {
    return dispatcher.handle(BusRequest("c", "r", Long.MAX_VALUE, accountId, idempotencyKey = null, query = query))
  }

  private companion object {
    const val BLOB = 1L
    const val ROUTES = RoutesCollection.ID
  }
}
