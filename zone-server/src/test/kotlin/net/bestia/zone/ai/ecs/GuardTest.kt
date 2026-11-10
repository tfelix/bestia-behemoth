package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.battle.damage.wardPlayer
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A bestia under GUARD orders holding the area around its home. The wolf profile's area is 24 around home. */
class GuardTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun guard(): EntityId {
    val id = ai.spawnMob("aggressive_melee", home, orders = AiConfig(BestiaJob.GUARD))
    ai.world.add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = id))
    ai.world.add(id, Account(MASTER_ACCOUNT))
    return id
  }

  /** A wild creature the guard can see, as the AOI index finds a spawned mob. */
  private fun wild(profileId: String, pos: Vec3L): EntityId {
    val id = ai.spawnMob(profileId, pos)
    ai.aoi.setEntityPosition(id, pos)
    return id
  }

  /** A master, with the account and party or guild it plays with. */
  private fun master(masterId: Long, pos: Vec3L): EntityId {
    val id = ai.spawnPlayer(pos)
    ai.world.getOrThrow(id, Master::class).masterId = masterId
    ai.world.add(id, Account(masterId * 10))
    return id
  }

  private fun assertNeverTargets(guard: EntityId, other: EntityId, why: String) {
    repeat(20 * 5) {
      ai.tick()
      assertNotEquals(other, targetOf(guard), why)
    }
  }

  private fun targetOf(id: EntityId): EntityId? {
    return ai.beliefOf(id, BestiaDomain.TARGET_ID)
  }

  @Test
  fun `a guard attacks a stranger inside its area`() {
    val guard = guard()
    val stranger = ai.spawnPlayer(Vec3L(5, 0, 0))

    ai.tickUntilGoal(guard, "KillEnemy")

    assertEquals(stranger, targetOf(guard))
  }

  @Test
  fun `a guard lets a stranger go once it leaves the area and walks back to the middle`() {
    val guard = guard()
    ai.teleport(guard, Vec3L(20, 0, 0))
    val stranger = ai.spawnPlayer(Vec3L(23, 0, 0))
    ai.tickUntilGoal(guard, "KillEnemy")

    ai.teleport(stranger, Vec3L(27, 0, 0))

    ai.tickUntilGoal(guard, "ReturnToMiddle")
    ai.tickUntil(describe = { "it stopped at ${ai.positionOf(guard)}" }) {
      ai.positionOf(guard).distance(home) <= BestiaDomain.ARRIVAL_RADIUS
    }
  }

  @Test
  fun `a guard does not chase an attacker out of its area`() {
    val guard = guard()
    val stranger = ai.spawnPlayer(Vec3L(27, 0, 0))
    ai.teleport(guard, Vec3L(20, 0, 0))

    repeat(20 * 5) {
      ai.recordHit(guard, stranger)
      ai.tick()
      assertNotEquals(stranger, targetOf(guard), "it went after an attacker outside its area")
    }
  }

  @Test
  fun `a guard attacks a wild hunter inside its area`() {
    val guard = guard()
    val wolf = wild("aggressive_melee", Vec3L(5, 0, 0))

    ai.tickUntilGoal(guard, "KillEnemy")

    assertEquals(wolf, targetOf(guard))
  }

  @Test
  fun `a guard leaves a grazer alone`() {
    val guard = guard()
    val deer = wild("passive_wanderer", Vec3L(5, 0, 0))

    repeat(20 * 5) {
      ai.tick()
      assertNotEquals(deer, targetOf(guard), "it attacked a grazer that never attacked it")
    }
  }

  @Test
  fun `a guard ignores a wild hunter outside its area`() {
    val guard = guard()
    ai.teleport(guard, Vec3L(20, 0, 0))
    val wolf = wild("aggressive_melee", Vec3L(27, 0, 0))

    repeat(20 * 5) {
      ai.tick()
      assertNotEquals(wolf, targetOf(guard), "it went after a wolf outside its area")
    }
  }

  @Test
  fun `a guard attacks another player's bestia inside its area`() {
    val guard = guard()
    master(STRANGER_ID, Vec3L(100, 0, 0))
    val strangersBestia = ai.spawnOwnedBestia(Vec3L(5, 0, 0), masterId = STRANGER_ID)

    ai.tickUntilGoal(guard, "KillEnemy")

    assertEquals(strangersBestia, targetOf(guard))
  }

  @Test
  fun `a guard spares its own master and the master's other bestias`() {
    val guard = guard()
    val master = master(MASTER_ID, Vec3L(4, 0, 0))
    val sibling = ai.spawnOwnedBestia(Vec3L(5, 0, 0), masterId = MASTER_ID)

    assertNeverTargets(guard, master, "it attacked its own master")
    assertNeverTargets(guard, sibling, "it attacked its master's other bestia")
  }

  @Test
  fun `a guard spares the master's party`() {
    val guard = guard()
    val friend = master(STRANGER_ID, Vec3L(5, 0, 0))
    val party = setOf(PartyMembership.Member(MASTER_ID * 10, MASTER_ID), PartyMembership.Member(STRANGER_ID * 10, STRANGER_ID))
    ai.world.add(friend, PartyMembership(partyId = 1L, members = party, expShare = PartyExpShare.EACH_TAKE))

    assertNeverTargets(guard, friend, "it attacked a party member")
  }

  @Test
  fun `a guard spares the master's guild and its bestias`() {
    val guard = guard()
    val friend = master(STRANGER_ID, Vec3L(4, 0, 0))
    val guild = setOf(GuildMembership.Member(MASTER_ID * 10, MASTER_ID), GuildMembership.Member(STRANGER_ID * 10, STRANGER_ID))
    ai.world.add(friend, GuildMembership(guildId = 1L, taxPercent = 0, members = guild))
    val friendsBestia = ai.spawnOwnedBestia(Vec3L(5, 0, 0), masterId = STRANGER_ID)

    assertNeverTargets(guard, friend, "it attacked a guild member")
    assertNeverTargets(guard, friendsBestia, "it attacked a guild member's bestia")
  }

  @Test
  fun `a guard spares a stranger inside a ward stone's field`() {
    val guard = guard()
    val stranger = ai.spawnPlayer(Vec3L(5, 0, 0))
    ai.world.wardPlayer(stranger)

    assertNeverTargets(guard, stranger, "it attacked a warded master")
  }

  private companion object {
    const val MASTER_ID = 7L
    const val MASTER_ACCOUNT = MASTER_ID * 10
    const val STRANGER_ID = 2L
  }
}
