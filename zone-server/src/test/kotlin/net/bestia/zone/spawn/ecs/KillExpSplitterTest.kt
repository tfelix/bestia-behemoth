package net.bestia.zone.spawn.ecs

import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.spawn.ecs.KillExpSplitter.ExpCredit
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Who gets a kill's EXP. Even share pools an attacker's portion with the bonus and splits it between the members
 * near the kill; with nobody to share with the attacker keeps it, bonus-free.
 */
class KillExpSplitterTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val rules = PartyRulesConfig()
  private val sut = KillExpSplitter(sessions, rules)

  private val members = setOf(
    PartyMembership.Member(A, MASTER_A),
    PartyMembership.Member(B, MASTER_B),
    PartyMembership.Member(C, MASTER_C),
  )

  private fun master(accountId: Long, masterId: Long, x: Long, share: PartyExpShare? = PartyExpShare.EVEN_SHARE): EntityId {
    val id = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, Master(masterId))
      add(id, Position.fromVec3(Vec3L(x, 0, 0)))
      share?.let { add(id, PartyMembership(PARTY, members, it)) }
    }
    sessions.activateSession(accountId, masterId, id)
    return id
  }

  private fun bestia(accountId: Long, masterId: Long, x: Long): EntityId {
    val id = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, OwnedBestia(masterId, playerBestiaId = 77L))
      add(id, Position.fromVec3(Vec3L(x, 0, 0)))
    }
    sessions.registerPlayerBestiaEntity(accountId, masterId, 77L, id)
    return id
  }

  private fun split(earned: Map<EntityId, Int>) = sut.split(world, KILLED_AT, earned)

  @Test
  fun `even share pools the portion with the bonus and splits it between the members near the kill`() {
    val a = master(A, MASTER_A, x = 0)
    val b = master(B, MASTER_B, x = 60)
    master(C, MASTER_C, x = 500)

    // 100 * 1.3 = 130 between two: C is 500 m away.
    assertEquals(listOf(ExpCredit(a, a, 65), ExpCredit(b, b, 65)), split(mapOf(a to 100)))
  }

  @Test
  fun `each take leaves every attacker its own portion`() {
    val a = master(A, MASTER_A, x = 0, share = PartyExpShare.EACH_TAKE)
    master(B, MASTER_B, x = 10, share = PartyExpShare.EACH_TAKE)

    assertEquals(listOf(ExpCredit(a, a, 100)), split(mapOf(a to 100)))
  }

  /** The bonus is for sharing: a member farming alone in an Even share party gets no more than outside one. */
  @Test
  fun `alone near the kill, the attacker keeps its portion without a bonus`() {
    val a = master(A, MASTER_A, x = 0)
    master(B, MASTER_B, x = 500)

    assertEquals(listOf(ExpCredit(a, a, 100)), split(mapOf(a to 100)))
  }

  /** The attacker's account always counts, wherever its master stands - its own damage earned it. */
  @Test
  fun `the attacker's master shares even when it stands far from the kill`() {
    val a = master(A, MASTER_A, x = 900)
    val b = master(B, MASTER_B, x = 0)

    assertEquals(listOf(ExpCredit(a, a, 65), ExpCredit(b, b, 65)), split(mapOf(a to 100)))
  }

  @Test
  fun `a member counts when the bestia it plays is near the kill, though its master is not`() {
    val a = master(A, MASTER_A, x = 0)
    val b = master(B, MASTER_B, x = 900)
    sessions.activateEntity(B, bestia(B, MASTER_B, x = 20))

    assertEquals(listOf(ExpCredit(a, a, 65), ExpCredit(b, b, 65)), split(mapOf(a to 100)))
  }

  /** The party is the master's: an account playing its other master is not in it. */
  @Test
  fun `a member whose account plays another master gets no share`() {
    val a = master(A, MASTER_A, x = 0)
    master(B, OTHER_MASTER, x = 10, share = null)

    assertEquals(listOf(ExpCredit(a, a, 100)), split(mapOf(a to 100)))
  }

  @Test
  fun `a bestia's portion is shared through its owner's master`() {
    val a = master(A, MASTER_A, x = 0)
    val b = master(B, MASTER_B, x = 10)
    val pet = bestia(A, MASTER_A, x = 5)

    assertEquals(listOf(ExpCredit(a, a, 65), ExpCredit(b, b, 65)), split(mapOf(pet to 100)))
  }

  @Test
  fun `outside a party a bestia keeps its own portion, with its master to tax`() {
    val a = master(A, MASTER_A, x = 0, share = null)
    val pet = bestia(A, MASTER_A, x = 5)

    assertEquals(listOf(ExpCredit(pet, a, 100)), split(mapOf(pet to 100)))
  }

  @Test
  fun `a bestia whose owner plays another master is nobody's to tax or share`() {
    master(A, OTHER_MASTER, x = 0, share = null)
    val pet = bestia(A, MASTER_A, x = 5)

    assertEquals(listOf(ExpCredit(pet, null, 100)), split(mapOf(pet to 100)))
  }

  @Test
  fun `a mob's helpers and nothing-earned portions are left alone`() {
    val a = master(A, MASTER_A, x = 0, share = null)
    val wild = world.createEntity { }

    assertEquals(listOf(ExpCredit(wild, null, 40)), split(mapOf(a to 0, wild to 40)))
  }

  private companion object {
    const val A = 1L
    const val B = 2L
    const val C = 3L
    const val MASTER_A = 10L
    const val MASTER_B = 20L
    const val MASTER_C = 30L
    const val OTHER_MASTER = 21L
    const val PARTY = 5L
    val KILLED_AT = Vec3L(0, 0, 0)
  }
}
