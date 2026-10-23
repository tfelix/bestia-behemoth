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
import net.bestia.zone.compendium.CompendiumStore
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
  fun `a master keeps, renames and forgets routes, and a stranger touches none`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()
    val points = listOf(MapPoint(100.0, 200.0), MapPoint(900.0, 950.0))

    val saved = ask(1L, ZoneQuery.SaveRoute(master.id, points, name = "")).answer as ZoneAnswer.RouteList
    val route = saved.routes.last()
    assertEquals(false, route.customName)
    assertEquals(points, route.waypoints)

    try {
      val renamed = ask(1L, ZoneQuery.RenameRoute(master.id, route.id, "Ore run")).answer as ZoneAnswer.RouteList
      assertEquals("Ore run", renamed.routes.single { it.id == route.id }.name)
      assertEquals(Status.NOT_FOUND, ask(2L, ZoneQuery.RenameRoute(master.id, route.id, "Mine")).status)
    } finally {
      val left = ask(1L, ZoneQuery.DeleteRoute(master.id, route.id)).answer as ZoneAnswer.RouteList
      assertTrue(left.routes.none { it.id == route.id })
    }
  }

  @Test
  fun `a bestia that is not in the world cannot be sent off, and a stranger's never`() {
    val master = masters.findAllByAccountIdOrderBySlot(1L).first()
    val bestia = playerBestias.findAllByMasterId(master.id).first()
    val order = ZoneQuery.SendOnJourney(master.id, bestia.id, listOf(MapPoint(10.0, 10.0)))

    assertEquals(Status.NOT_FOUND, ask(2L, order).status)
    if (bestia.entityId == null) assertEquals(Status.NOT_FOUND, ask(1L, order).status)
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
  }
}
