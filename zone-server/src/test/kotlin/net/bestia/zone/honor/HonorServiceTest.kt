package net.bestia.zone.honor

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.honor.persistence.LocalHonor.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HonorServiceTest {

  private val fixture = HonorFixture()
  private val world = fixture.world
  private val service = fixture.service

  @Test
  fun `an act nobody saw costs nothing`() {
    val killer = fixture.master(WILDS)

    val recorded = service.record(world, HonorAct.KILL_PLAYER, killer, WILDS)

    assertFalse(recorded.seen)
    assertEquals(20, globalOf(killer))
  }

  @Test
  fun `a player witness costs only the global part`() {
    val killer = fixture.master(WILDS)
    fixture.master(WILDS + Vec3L(10, 0, 0))

    val recorded = service.record(world, HonorAct.KILL_PLAYER, killer, WILDS)

    assertTrue(recorded.seen)
    assertEquals(10, globalOf(killer))
    assertTrue(recorded.grudges.isEmpty())
    assertNull(recorded.settlement)
  }

  @Test
  fun `a townsperson who saw it holds a grudge and the town does not know yet`() {
    fixture.settlementAt = { HonorFixture.VILLAGE }
    val killer = fixture.master(WILDS)
    val baker = fixture.townsperson(WILDS + Vec3L(5, 0, 0))

    service.record(world, HonorAct.KILL_PLAYER, killer, WILDS)

    val masterId = fixture.masterIdOf(killer)
    assertEquals(-50, fixture.localHonor.ledgerOf(masterId, Scope.GRUDGE, baker)!!.valueAt(fixture.now, fixture.rules.grudge))
    assertEquals(10, service.settlementValue(masterId, HonorFixture.VILLAGE))
    assertEquals(10 + 10 - 50, service.combined(world, killer, HonorFixture.VILLAGE, baker))
  }

  @Test
  fun `inside a settlement with nobody of the town in sight the settlement hears of it`() {
    fixture.settlementAt = { HonorFixture.VILLAGE }
    val vandal = fixture.master(WILDS)

    val recorded = service.record(world, HonorAct.DAMAGE_BUILDING, vandal, WILDS)

    assertEquals(HonorFixture.VILLAGE, recorded.settlement)
    assertEquals(0, service.settlementValue(fixture.masterIdOf(vandal), HonorFixture.VILLAGE))
  }

  @Test
  fun `a ward stone sees everything and doubles the loss`() {
    val killer = fixture.master(WILDS)
    fixture.ward(killer)

    val recorded = service.record(world, HonorAct.KILL_NPC, killer, WILDS)

    assertTrue(recorded.seen)
    assertEquals(0, globalOf(killer))
  }

  @Test
  fun `a good deed inside a ward field earns half again`() {
    val helper = fixture.master(WILDS)
    fixture.master(WILDS + Vec3L(10, 0, 0))
    fixture.ward(helper)

    service.record(world, HonorAct.HELP_NPC, helper, WILDS)

    assertEquals(23, globalOf(helper))
  }

  private fun globalOf(entity: Long): Int {
    return world.get(entity, Honor::class)!!.shownValue
  }

  private companion object {
    val WILDS = Vec3L(10_000, 10_000, 0)
  }
}
