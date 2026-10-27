package net.bestia.bus

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.contract.ZoneQuery
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BusFrameTest {

  @Test
  fun `a request survives the round trip, singleton queries included`() {
    val queries = listOf(
      ZoneQuery.ListMasters,
      ZoneQuery.MapTile(masterId = 7, level = 3, x = -2, y = 9, ifNoneMatch = "\"w1-3/-2/9\""),
      ZoneQuery.HabitatTile(masterId = 7, speciesId = 1, level = 3, x = -2, y = 9, ifNoneMatch = null),
      ZoneQuery.SetStance(masterId = 7, playerBestiaId = 11, stance = "HOLD", aggression = 20),
      ZoneQuery.SendChat(masterId = 7, channel = "PARTY", text = "on my way"),
      ZoneQuery.Compendium(masterId = 7),
      ZoneQuery.SendOnJourney(masterId = 7, playerBestiaId = 11, waypoints = listOf(MapPoint(1.5, 2.5))),
      ZoneQuery.SendOnJourney(7, playerBestiaId = 11, waypoints = emptyList(), routeKey = "k3", reverse = true),
      ZoneQuery.StopJourney(masterId = 7, playerBestiaId = 11),
      ZoneQuery.NotebookList(masterId = 7, collection = "routes"),
      ZoneQuery.NotebookList(masterId = null, collection = "keybindings"),
      ZoneQuery.NotebookPut(7, "routes", key = null, json = """{"name":"Ore run"}""", baseRevision = 4),
      ZoneQuery.NotebookDelete(7, "marks", key = "k3", baseRevision = null),
    )

    for (query in queries) {
      val request = BusRequest("c-1", "api:a:replies", 123L, accountId = 42, idempotencyKey = "k", query = query)

      assertEquals(request, BusFrame.decodeRequest(BusFrame.encode(request)))
    }
  }

  @Test
  fun `a reply keeps its answer and its binary body apart`() {
    val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 1, 2)
    val reply = BusReply.ok("c-2", ZoneAnswer.Tile(etag = "\"e\"", shared = true), png)

    val decoded = BusFrame.decodeReply(BusFrame.encode(reply))

    assertEquals("c-2", decoded.correlationId)
    assertEquals(BusReply.Status.OK, decoded.status)
    assertEquals(ZoneAnswer.Tile("\"e\"", true), decoded.answer)
    assertContentEquals(png, decoded.body)
  }

  /** Every answer, field for field: a property Jackson names differently from its constructor would read as 0. */
  @Test
  fun `every answer survives the round trip`() {
    val answers = listOf(
      ZoneAnswer.Masters(listOf(ZoneAnswer.Masters.Master(1, 0, "Ayla", 3, "Ashfen", true))),
      ZoneAnswer.Sheet(1, "Ayla", 3, 40, 1, 2, 10, 11, 12, 13, 14, 15, 18, "Ashfen", 61134.0, 27383.0, false, 99),
      ZoneAnswer.Roster(listOf(ZoneAnswer.Roster.Bestia(2, "Blobby", 1, "blob", 4, "HOLD", 20, true, 1.5, 2.5, 7, 4.0))),
      ZoneAnswer.Bag(listOf(ZoneAnswer.Bag.Holder("MASTER", 1, "Ayla", listOf(ZoneAnswer.Bag.Slot(3, "apple", 5, null, 0, 0, 0))))),
      ZoneAnswer.PartyInfo(ZoneAnswer.PartyInfo.Party("P", 1, listOf(ZoneAnswer.PartyInfo.Member(1, "Ayla", 3, true)))),
      ZoneAnswer.CatalogData("v", listOf(ZoneAnswer.CatalogData.Item(1, "apple", "USABLE", 15, null, true)),
        listOf(ZoneAnswer.CatalogData.Species(1, "blob", "FORMLESS", "WATER", 1, listOf("GRASSLAND"), true))),
      ZoneAnswer.CompendiumData(listOf(ZoneAnswer.CompendiumData.Entry(speciesId = 1, caught = true, firstSeenEpochMs = 5))),
      ZoneAnswer.MapInfo("w", 256, 0, 9, 128000.0, 128000.0, 1.0),
      ZoneAnswer.Places("\"e\"", false, listOf(ZoneAnswer.Places.Place(1, "SETTLEMENT", 3.5, 4.5, "Ashfen", "TOWN", null))),
      ZoneAnswer.Services("\"s\"", listOf(ZoneAnswer.Services.Service(9, "INN", "The Boar", 1.0, 2.0, "Ashfen", 6, 22))),
      ZoneAnswer.TicketOwner(42),
      ZoneAnswer.Stance(2, "HOLD", 20),
      ZoneAnswer.ChatSent(null),
      ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_GUILD),
      ZoneAnswer.Roster(
        listOf(
          ZoneAnswer.Roster.Bestia(
            2, "Blobby", 1, "blob", 4, "HOLD", 20, true, 1.5, 2.5, 7, 4.0,
            ZoneAnswer.Journey("Ore run", listOf(MapPoint(9.0, 9.0)), 0, listOf(MapPoint(5.0, 5.0))),
          )
        )
      ),
      ZoneAnswer.Notebook(5, listOf(ZoneAnswer.Notebook.Entry("k3", 5, """{"name":"Ore run"}"""))),
      ZoneAnswer.NotebookWritten(6, "k3", null),
      ZoneAnswer.Done,
    )

    for (answer in answers) {
      val decoded = BusFrame.decodeReply(BusFrame.encode(BusReply.ok("c", answer))).answer
      assertEquals(answer, decoded)
    }
  }

  @Test
  fun `events decode to their own type`() {
    val events = listOf(
      ZoneEvent.BestiaDiedAway(accountId = 1, masterId = 2, playerBestiaId = 3, bestiaName = null, species = "blob"),
      ZoneEvent.ChatLine(accountId = 1, masterId = 2, channel = "GUILD", senderName = "Ayla", text = "hi", sentAtEpochMs = 5),
      ZoneEvent.CompendiumChanged(accountId = 1, masterId = 2),
      ZoneEvent.NotebookChanged(1, masterId = null, collection = "keybindings", key = "_", revision = 3, deleted = false, json = "{}"),
      ZoneEvent.JourneyEnded(1, 2, 3, "Rex", "wolf", "Ore run", "STUCK", MapPoint(1.0, 2.0), whilePlaying = false),
    )

    for (event in events) {
      val json = BusJson.mapper.writeValueAsString(event)

      assertEquals(event, BusJson.mapper.readValue(json, ZoneEvent::class.java))
    }
  }
}
