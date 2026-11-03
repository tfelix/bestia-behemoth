package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.townsfolk.ecs.TownsfolkIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HonorEventsTest {

  private val fixture = HonorFixture()
  private val world = fixture.world
  private val events = HonorEvents(fixture.service, fixture.statusEffects)

  private val thief = fixture.master(HERE)

  @Test
  fun `a theft somebody saw costs honor and marks the thief`() {
    fixture.master(HERE + Vec3L(10, 0, 0))

    events.stole(world, thief, StolenTier.RARE, HERE)

    assertEquals(10, globalOf(thief))
    assertTrue(world.get(thief, StatusEffects::class)!!.hasEffect(StatusEffectId.THIEF.id))
  }

  @Test
  fun `a theft nobody saw leaves no mark`() {
    events.stole(world, thief, StolenTier.RARE, HERE)

    assertFalse(world.get(thief, StatusEffects::class)!!.hasEffect(StatusEffectId.THIEF.id))
  }

  @Test
  fun `robbing an Honorable Citizen costs more`() {
    fixture.master(HERE + Vec3L(10, 0, 0))

    events.stole(world, thief, StolenTier.RARE, HERE, ownerHonorableCitizen = 5)

    assertEquals(0, globalOf(thief))
  }

  @Test
  fun `the broker of an abandoned commission holds the grudge unseen`() {
    val broker = TownsfolkIdentity.of(HonorFixture.VILLAGE, household = 7, member = 0)

    events.abandonedCommission(world, thief, broker)

    assertEquals(15, globalOf(thief))
    assertEquals(15 + 10 - 20, fixture.service.combined(world, thief, HonorFixture.VILLAGE, broker))
  }

  private fun globalOf(entity: Long): Int {
    return world.get(entity, Honor::class)!!.shownValue
  }

  private companion object {
    val HERE = Vec3L(10_000, 10_000, 0)
  }
}
