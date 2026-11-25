package net.bestia.zone.worldevent.gm

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.place.Provinces
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.worldevent.WorldEventDirector
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A GM stands in cell (10, 10). West of x = 0 lies province 0, the rest is province 1; a rift runs in 1. */
class GmManaLayerTest {

  private val world = testWorld()
  private val gm = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(10 * 128 + 5, 10 * 128 + 5, 0))) }

  private var authorities = setOf(Authority.WORLD_EVENTS)
  private val connections = mockk<ConnectionInfoService> {
    every { getAuthorities(7) } answers { authorities }
    every { getSelectedMasterEntityId(7) } returns gm
  }
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
  }
  private val worldService = mockk<WorldService> { every { isLoaded } returns true }
  private val mana = mockk<ManaConcentrationService> {
    every { cellAt(any()) } answers { firstArg<Vec3L>().let { Cell((it.x / 128).toInt(), (it.y / 128).toInt()) } }
    every { densityOf(any()) } returns 0.2
    every { densityOf(Cell(10, 10)) } returns 0.72
  }
  private val tiers = mockk<ManaTiers> {
    every { tierOf(any()) } returns ManaTier.CALM
    every { tierOf(Cell(10, 10)) } returns ManaTier.EVENT
  }
  private val west = province(0)
  private val east = province(1)
  private val split = mockk<Provinces> { every { provinces } returns listOf(west, east) }
  private val provinces = mockk<ProvinceService> {
    every { provinceAt(any(), any()) } answers { if (firstArg<Double>() < 0.0) west else east }
    every { provinces } returns split
    every { isSanctuary(any()) } answers { firstArg<Province>() == west }
  }
  private val rift = WorldEventInstance(eventId = "rift", provinceIndex = 1, x = 1_300, y = 1_300).apply { id = 3 }
  private val director = mockk<WorldEventDirector> {
    every { running() } returns listOf(rift)
    every { provinceStates() } returns mapOf(0 to WorldEventDirector.ProvinceState(1.0, 0), 1 to WorldEventDirector.ProvinceState(2.5, 600))
  }
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = GmManaLayer(connections, view, worldService, mana, tiers, provinces, director, out)

  @Test
  fun `turning it on sends the field, the provinces and the events around the GM`() {
    sut.turnOn(7)

    val layer = sent()
    assertEquals(41, layer.width)
    assertEquals(41 * 41, layer.cells.size)
    assertEquals(-10 * 128.0, layer.westMetres)

    val own = layer.cells[20 * 41 + 20]
    assertEquals(0.72, own.density)
    assertEquals(ManaTier.EVENT, own.tier)
    assertEquals(0, layer.cells.first().province)
    assertEquals(1, own.province)

    assertEquals(listOf(true, false), layer.provinces.map { it.sanctuary })
    assertEquals(600L, layer.provinces[1].quietSeconds)
    assertEquals(listOf("rift"), layer.events.map { it.eventId })
  }

  @Test
  fun `a GM who loses the authority stops receiving it`() {
    sut.turnOn(7)
    authorities = emptySet()

    sut.sendAll()
    authorities = setOf(Authority.WORLD_EVENTS)
    sut.sendAll()

    verify(exactly = 1) { out.sendToPlayer(7, any<GmManaLayerSMSG>()) }
  }

  @Test
  fun `turning it off clears the layer`() {
    sut.turnOn(7)
    sut.turnOff(7)
    sut.sendAll()

    verify(exactly = 1) { out.sendToPlayer(7, GmManaLayerSMSG.CLEARED) }
    verify(exactly = 2) { out.sendToPlayer(7, any<GmManaLayerSMSG>()) }
  }

  private fun sent(): GmManaLayerSMSG {
    val layer = slot<GmManaLayerSMSG>()
    verify { out.sendToPlayer(7, capture(layer)) }
    return layer.captured
  }

  private fun province(index: Int) =
    Province(index, "P$index", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), region = index), listOf(index))
}
