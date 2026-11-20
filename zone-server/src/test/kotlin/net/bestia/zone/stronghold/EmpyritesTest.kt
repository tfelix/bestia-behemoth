package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.ecs.EmpyriteSystem
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmpyritesTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val held = mutableListOf<Stronghold>()
  private val sprouts = mockk<EmpyriteSeedSprouts>(relaxed = true)

  private val strongholds = mockk<StrongholdRegistry> {
    every { all() } answers { held.toList() }
    every { on(any()) } answers { held.firstOrNull { it.rank == firstArg<Int>() } }
    every { put(any()) } answers { held.removeIf { it.rank == firstArg<Stronghold>().rank }; held.add(firstArg()) }
    every { remove(any()) } answers { held.removeIf { it.rank == firstArg<Int>() } }
  }

  private val empyrites = Empyrites(
    strongholds = strongholds,
    seeds = mockk { every { awake() } returns listOf(seed) },
    sprouts = sprouts,
    spots = mockk { every { standingAt(seed) } returns Vec3L(1_000, 1_000, 40) },
    config = StrongholdConfig(empyriteHp = 50_000, empyriteDefense = 20),
  )

  @Test
  fun `a finished site becomes a whole Empyrite of the guild`() {
    val site = Stronghold(seed.rank, GUILD, State.SITE, 40L, EPOCH, EPOCH)
    held.add(site)

    empyrites.stand(world, site)

    assertEquals(site.copy(state = State.STANDING, siteStructureId = null, empyriteHp = 50_000), held.single())
    val empyrite = assertNotNull(empyriteEntity())
    assertEquals(GuildOwned(GUILD), world.get(empyrite, GuildOwned::class))
    assertEquals(50_000, world.get(empyrite, Health::class)?.current)
  }

  @Test
  fun `a restart raises the Empyrite with the health it had`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 31_000))

    empyrites.raiseAll(world)

    assertEquals(31_000, world.get(assertNotNull(empyriteEntity()), Health::class)?.current)
  }

  @Test
  fun `a broken Empyrite ends the Stronghold, and the seed sprouts again`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 1))
    empyrites.raiseAll(world)
    world.add(assertNotNull(empyriteEntity()), Dead())

    EmpyriteSystem(empyrites).update(world, 0.05f)

    assertTrue(held.isEmpty())
    verify { sprouts.raise(world, seed) }
  }

  @Test
  fun `an Empyrite nobody hit for five minutes heals one percent a minute`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 10_000))
    empyrites.raiseAll(world)
    val system = EmpyriteSystem(empyrites)

    repeat(300) { system.update(world, 1f) }
    val healedFrom = world.get(assertNotNull(empyriteEntity()), Health::class)!!.current
    repeat(60) { system.update(world, 1f) }

    assertEquals(healedFrom + 500, world.get(assertNotNull(empyriteEntity()), Health::class)!!.current, 2)
  }

  private fun empyriteEntity(): EntityId? {
    var found: EntityId? = null
    world.query(Empyrite::class).each { id -> found = id }
    return found
  }

  private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
    assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")
  }

  private companion object {
    const val GUILD = 1L
    val EPOCH: Instant = Instant.EPOCH
  }
}
