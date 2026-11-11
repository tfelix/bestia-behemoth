package net.bestia.bus

import net.bestia.bus.contract.OrderRuleLine
import net.bestia.bus.contract.OrderSheet
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.OrderNumbers
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
      ZoneQuery.SetOrders(masterId = 7, playerBestiaId = 11, job = "GUARD", courage = "BOLD"),
      ZoneQuery.SetOrders(7, 11, "GATHER", "STEADY", fallbackJob = "CRAFT", overrides = OrderNumbers(haulAtLoadPct = 60),
        rules = listOf(OrderRuleLine("STAMINA_BELOW", 30, "EAT"), OrderRuleLine("NIGHT", null, "REST_AT_HOME"))),
      ZoneQuery.SetOrders(7, 11, "REST", "TIMID", overrides = OrderNumbers(eatBelowStaminaPct = 70), presetId = 3),
      ZoneQuery.OrderPresets(7),
      ZoneQuery.SaveOrderPreset(7, null, "Camp", OrderSheet("GATHER", "BOLD", rules = listOf(OrderRuleLine("DAY", null, "KEEP_WORKING")))),
      ZoneQuery.DeleteOrderPreset(7, 3),
      ZoneQuery.Diary(7),
      ZoneQuery.BestiaPlaces(7),
      ZoneQuery.SaveAreaPlace(7, placeId = null, name = "North field", eastMetres = 40.0, northMetres = 80.0, radius = 16),
      ZoneQuery.SaveChestPlace(7, placeId = 3, name = "Camp chest", chestStructureId = 9),
      ZoneQuery.DeletePlace(7, placeId = 3),
      ZoneQuery.SetOrders(7, 11, "GATHER", "STEADY", areaPlaceId = 3, chestPlaceId = 4),
      ZoneQuery.Workshop(7),
      ZoneQuery.QueueAtStation(7, structureId = 5, recipeId = 42, amount = 3),
      ZoneQuery.CancelAtStation(7, structureId = 5, index = 1),
      ZoneQuery.MoveAtStation(7, structureId = 5, from = 1, to = 0),
      ZoneQuery.SetStationWorker(7, structureId = 5, playerBestiaId = null, anyFreeCrafter = true),
      ZoneQuery.SendChat(masterId = 7, channel = "PARTY", text = "on my way"),
      ZoneQuery.Compendium(masterId = 7),
      ZoneQuery.SendOnJourney(masterId = 7, playerBestiaId = 11, waypoints = listOf(MapPoint(1.5, 2.5))),
      ZoneQuery.SendOnJourney(7, playerBestiaId = 11, waypoints = emptyList(), routeKey = "k3", reverse = true),
      ZoneQuery.StopJourney(masterId = 7, playerBestiaId = 11),
      ZoneQuery.NotebookList(masterId = 7, collection = "routes"),
      ZoneQuery.NotebookList(masterId = null, collection = "keybindings"),
      ZoneQuery.NotebookPut(7, "routes", key = null, json = """{"name":"Ore run"}""", baseRevision = 4),
      ZoneQuery.NotebookDelete(7, "marks", key = "k3", baseRevision = null),
      ZoneQuery.Letters(7),
      ZoneQuery.SendLetter(7, recipientName = "Ayla", subject = "Bridge", body = "It is down.\nTake the ford."),
      ZoneQuery.ReadLetter(7, letterId = 3),
      ZoneQuery.DeleteLetter(7, letterId = 3),
      ZoneQuery.Parcels(7),
      ZoneQuery.Theodolites(7),
      ZoneQuery.StartSurvey(7, structureId = 3, km = 5),
      ZoneQuery.SurveyStops(7, structureId = 3, stopsMs = listOf(120, 0, 2399)),
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
      ZoneAnswer.Roster(listOf(ZoneAnswer.Roster.Bestia(2, "Blobby", 1, "blob", 4, "GUARD", "BOLD", true, 1.5, 2.5, 7, 4.0))),
      ZoneAnswer.Bag(listOf(ZoneAnswer.Bag.Holder("MASTER", 1, "Ayla", listOf(ZoneAnswer.Bag.Slot(3, "apple", 5, null, 0, 0, 0))))),
      ZoneAnswer.PartyInfo(ZoneAnswer.PartyInfo.Party("P", 1, listOf(ZoneAnswer.PartyInfo.Member(1, "Ayla", 3, true)))),
      ZoneAnswer.CatalogData("v", listOf(ZoneAnswer.CatalogData.Item(1, "apple", "USABLE", 15, null, true)),
        listOf(ZoneAnswer.CatalogData.Species(1, "blob", "FORMLESS", "WATER", 1, listOf("GRASSLAND"), true))),
      ZoneAnswer.CompendiumData(listOf(ZoneAnswer.CompendiumData.Entry(speciesId = 1, caught = true, firstSeenEpochMs = 5))),
      ZoneAnswer.MapInfo("w", 256, 0, 9, 128000.0, 128000.0, 1.0),
      ZoneAnswer.Places("\"e\"", false, listOf(ZoneAnswer.Places.Place(1, "SETTLEMENT", 3.5, 4.5, "Ashfen", "TOWN", null))),
      ZoneAnswer.Services("\"s\"", listOf(ZoneAnswer.Services.Service(9, "INN", "The Boar", 1.0, 2.0, "Ashfen", 6, 22))),
      ZoneAnswer.TicketOwner(42),
      ZoneAnswer.Orders(2, "GUARD", "BOLD"),
      ZoneAnswer.BestiaPlaces(
        listOf(ZoneAnswer.BestiaPlaces.BestiaPlace(3, "North field", "AREA", 40.0, 80.0, 16, null)),
        listOf(ZoneAnswer.BestiaPlaces.ChestSpot(9, 1.0, 2.0)),
      ),
      ZoneAnswer.Diary(listOf(ZoneAnswer.Diary.DiaryEntry(3, 99, "HAULED", mapOf("log" to 12), null, null))),
      ZoneAnswer.Workshop(
        listOf(
          ZoneAnswer.Workshop.StationView(
            5, "WORKBENCH", 1.0, 2.0, listOf(ZoneAnswer.Workshop.QueueLine(42, "chest_kit", 3, 1)), "BESTIA", 11, null,
            hasChest = true, affordable = listOf(ZoneAnswer.Workshop.AffordableRecipe(42, "chest_kit", 4)),
          )
        )
      ),
      ZoneAnswer.OrderPresets(listOf(ZoneAnswer.OrderPresets.Preset(3, "Camp", OrderSheet("MINE", "STEADY", "GUARD"), bestias = 2))),
      ZoneAnswer.Orders(2, "MINE", "BOLD", fallbackJob = "GUARD", overrides = OrderNumbers(retreatBelowHealthPct = 0)),
      ZoneAnswer.ChatSent(null),
      ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_GUILD),
      ZoneAnswer.Letters(listOf(ZoneAnswer.Letters.Letter(3, "Ayla", "Bridge", "It is down.", 5, 6, read = true))),
      ZoneAnswer.LetterSent(ZoneAnswer.LetterSent.RECIPIENT_UNKNOWN),
      ZoneAnswer.Parcels(
        listOf(ZoneAnswer.Parcels.Parcel(4, "Ayla", "Ashfen", 9, arrived = true, returning = false, listOf(ZoneAnswer.Parcels.Goods("apple", 3))))
      ),
      ZoneAnswer.Roster(
        listOf(
          ZoneAnswer.Roster.Bestia(
            2, "Blobby", 1, "blob", 4, "GUARD", "BOLD", true, 1.5, 2.5, 7, 4.0,
            ZoneAnswer.Journey("Ore run", listOf(MapPoint(9.0, 9.0)), 0, listOf(MapPoint(5.0, 5.0))),
          )
        )
      ),
      ZoneAnswer.Notebook(5, listOf(ZoneAnswer.Notebook.Entry("k3", 5, """{"name":"Ore run"}"""))),
      ZoneAnswer.NotebookWritten(6, "k3", null),
      ZoneAnswer.Done,
      ZoneAnswer.Theodolites(
        listOf(
          ZoneAnswer.Theodolites.Theodolite(3, MapPoint(1.5, 2.5), "SURVEYING", radiusKm = 3, remainingSeconds = 420),
          ZoneAnswer.Theodolites.Theodolite(4, MapPoint(3.0, 4.0), "IDLE", radiusKm = null, remainingSeconds = null),
        )
      ),
      ZoneAnswer.SurveyGame(3, swingPeriodMs = 1800, arcWidthDegrees = 20, arcCentresDegrees = listOf(-40, 0, 65)),
      ZoneAnswer.SurveyOutcome("WON"),
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
      ZoneEvent.LetterArrived(accountId = 1, masterId = 2, letterId = 3, senderName = "Ayla", subject = "Bridge"),
      ZoneEvent.ParcelArrived(accountId = 1, masterId = 2, parcelId = 4, senderName = "Ayla", officeName = "Ashfen"),
      ZoneEvent.CompendiumChanged(accountId = 1, masterId = 2),
      ZoneEvent.NotebookChanged(1, masterId = null, collection = "keybindings", key = "_", revision = 3, deleted = false, json = "{}"),
      ZoneEvent.JourneyEnded(1, 2, 3, "Rex", "wolf", "Ore run", "STUCK", MapPoint(1.0, 2.0), whilePlaying = false),
      ZoneEvent.BestiaHappening(1, 2, 3, "Rex", "wolf", "WORK_PAUSED", "CAMPFIRE", "STATION_OUT", whilePlaying = false),
    )

    for (event in events) {
      val json = BusJson.mapper.writeValueAsString(event)

      assertEquals(event, BusJson.mapper.readValue(json, ZoneEvent::class.java))
    }
  }
}
