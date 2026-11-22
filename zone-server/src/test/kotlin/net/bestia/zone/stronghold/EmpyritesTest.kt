package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.ecs.EmpyriteSystem
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmpyritesTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val held = mutableListOf<Stronghold>()
  private val sprouts = mockk<EmpyriteSeedSprouts>(relaxed = true)
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val stewards = mockk<Stewards>(relaxed = true)
  private val watchers = mockk<Watchers>(relaxed = true)
  private val news = mockk<StrongholdNews>(relaxed = true)

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
    statusEffects = statusEffects,
    stewards = stewards,
    watchers = watchers,
    news = news,
    config = StrongholdConfig(
      empyriteHp = 50_000, empyriteDefense = 20, empyriteHpPerDefense = 10_000, captureGraceMinutes = 10
    ),
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
    verify { news.stands(world, GUILD, seed.rank) }
  }

  @Test
  fun `a restart raises the Empyrite with the health it had`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 31_000))

    empyrites.raiseAll(world)

    assertEquals(31_000, world.get(assertNotNull(empyriteEntity()), Health::class)?.current)
  }

  @Test
  fun `the guild that strikes the breaking blow takes the Stronghold, whole and unharmable for a while`() {
    held.add(
      Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 1, hasSteward = true)
        .withWatchers(WatcherKind.MELEE, 3)
    )
    empyrites.raiseAll(world)
    val broken = assertNotNull(empyriteEntity())
    val enemy = world.createEntity { id ->
      add(id, Account(9L))
      add(id, GuildMembership(OTHER_GUILD, taxPercent = 5))
    }
    world.add(broken, TakenDamage().also { it.addDamage(enemy, 1) })
    world.add(broken, Dead())

    EmpyriteSystem(empyrites).update(world, 0.05f)

    assertEquals(OTHER_GUILD, held.single().guildId)
    assertEquals(50_000, held.single().empyriteHp)
    assertFalse(held.single().hasSteward)
    assertEquals(emptyMap(), held.single().watchers)
    verify { stewards.dismiss(world, seed.rank) }
    verify { watchers.dismissAll(world, seed.rank) }
    verify { news.captured(world, OTHER_GUILD, GUILD, seed.rank) }
    val reformed = assertNotNull(empyriteEntities().singleOrNull { it != broken })
    assertEquals(GuildOwned(OTHER_GUILD), world.get(reformed, GuildOwned::class))
    assertEquals(50_000, world.get(reformed, Health::class)?.current)
    verify { statusEffects.applyEffect(world, reformed, StatusEffectId.INVULNERABLE, level = 1, durationSeconds = 600.0) }
  }

  @Test
  fun `a capture costs every track one level, and the Empyrite forms at the health its Defense gives`() {
    val tracks = mapOf(StrongholdTrack.DEFENSE to TrackProgress(3, 2), StrongholdTrack.ECONOMY to TrackProgress(1, 0))
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 1, tracks = tracks))
    empyrites.raiseAll(world)
    val broken = assertNotNull(empyriteEntity())

    empyrites.broken(world, seed.rank, OTHER_GUILD)

    val captured = held.single()
    assertEquals(TrackProgress(2, 2), captured.progressOf(StrongholdTrack.DEFENSE))
    assertEquals(TrackProgress(0, 0), captured.progressOf(StrongholdTrack.ECONOMY))
    val reformed = assertNotNull(empyriteEntities().singleOrNull { it != broken })
    assertEquals(70_000, world.get(reformed, Health::class)?.current)
  }

  @Test
  fun `a new Defense level hardens a standing Empyrite without healing it`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 45_000))
    empyrites.raiseAll(world)
    held[0] = held[0].withProgress(StrongholdTrack.DEFENSE, TrackProgress(1, 0))

    empyrites.harden(world, seed.rank)

    val health = world.get(assertNotNull(empyriteEntity()), Health::class)!!
    assertEquals(60_000, health.max)
    assertEquals(55_000, health.current)
  }

  @Test
  fun `an Empyrite broken with nobody to take it ends the Stronghold, and the seed sprouts again`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, EPOCH, EPOCH, empyriteHp = 1))
    empyrites.raiseAll(world)
    world.add(assertNotNull(empyriteEntity()), Dead())

    EmpyriteSystem(empyrites).update(world, 0.05f)

    assertTrue(held.isEmpty())
    verify { sprouts.raise(world, seed) }
    verify { stewards.dismiss(world, seed.rank) }
    verify { news.lost(world, GUILD, seed.rank) }
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
    return empyriteEntities().singleOrNull()
  }

  private fun empyriteEntities(): List<EntityId> {
    val found = mutableListOf<EntityId>()
    world.query(Empyrite::class).each { id -> found.add(id) }
    return found
  }

  private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
    assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    val EPOCH: Instant = Instant.EPOCH
  }
}
