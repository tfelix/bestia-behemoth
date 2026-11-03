package net.bestia.zone.honor

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import net.bestia.zone.townsfolk.ecs.Townsfolk
import net.bestia.zone.townsfolk.ecs.TownsfolkDeaths
import kotlin.test.Test
import kotlin.test.assertEquals

class NpcKillReporterTest {

  private val deaths = mockk<TownsfolkDeaths>(relaxed = true)
  private val fixture = HonorFixture { f -> listOf(NpcKillReporter(f.service, f.masters, deaths)) }
  private val world = fixture.world

  @Test
  fun `killing a townsperson in front of another costs honor and a grudge`() {
    val killer = fixture.master(HERE)
    val witness = fixture.townsperson(HERE + Vec3L(10, 0, 0), household = 2)
    val victim = townspersonEntity(HERE + Vec3L(2, 0, 0), household = 1)

    kill(killer, victim)

    assertEquals(10, world.get(killer, Honor::class)!!.shownValue)
    assertEquals(10 - 60, fixture.service.combined(world, killer, settlement = null, townsperson = witness))
  }

  @Test
  fun `the dead are kept away until they are replaced`() {
    val killer = fixture.master(HERE)
    val victim = townspersonEntity(HERE, household = 1)

    kill(killer, victim)

    verify { deaths.died(world.get(victim, Townsfolk::class)!!.identity) }
  }

  private fun townspersonEntity(at: Vec3L, household: Int): Long {
    val identity = fixture.townsperson(at, household = household)
    var entity = 0L
    world.each(Townsfolk::class) { id, townsfolk -> if (townsfolk.identity == identity) entity = id }

    return entity
  }

  private fun kill(by: Long, victim: Long) {
    world.add(victim, TakenDamage().also { it.addDamage(by, 20) })
    world.add(victim, Dead())
    world.tick(0.1f)
  }

  private companion object {
    val HERE = Vec3L(10_000, 10_000, 0)
  }
}
