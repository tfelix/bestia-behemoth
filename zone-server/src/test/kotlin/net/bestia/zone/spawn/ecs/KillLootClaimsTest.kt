package net.bestia.zone.spawn.ecs

import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.item.ecs.LootClaim
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KillLootClaimsTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val sut = KillLootClaims(sessions, WorldRulesConfig(tickRate = 10))

  private val party = PartyMembership(
    PARTY,
    setOf(PartyMembership.Member(A, MASTER_A), PartyMembership.Member(B, MASTER_B), PartyMembership.Member(C, MASTER_C)),
    PartyExpShare.EVEN_SHARE,
  )

  private fun master(accountId: Long, masterId: Long, inParty: Boolean = true): EntityId {
    val id = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, Master(masterId))
      if (inParty) {
        add(id, party)
      }
    }
    sessions.activateSession(accountId, masterId, id)
    return id
  }

  private fun claimsAfter(vararg hits: Pair<EntityId, Int>): KillLootClaims.KillLoot? {
    val damage = TakenDamage().apply { hits.forEach { (source, amount) -> addDamage(source, amount) } }
    return sut.of(world, damage)
  }

  @Test
  fun `a lone hunter holds the loot alone`() {
    val stranger = master(STRANGER, MASTER_STRANGER, inParty = false)

    val loot = claimsAfter(stranger to 10)

    assertEquals(STRANGER, loot?.leadAccount)
    assertEquals(listOf(LootClaim(setOf(STRANGER), 6f)), loot?.claimsForNextDrop())
  }

  @Test
  fun `a party's damage adds up against a stranger who hit harder than each member`() {
    val a = master(A, MASTER_A)
    val b = master(B, MASTER_B)
    master(C, MASTER_C)
    val stranger = master(STRANGER, MASTER_STRANGER, inParty = false)

    val loot = claimsAfter(stranger to 40, a to 35, b to 25)

    assertEquals(A, loot?.leadAccount)
    assertEquals(listOf(LootClaim(setOf(A, B, C), 6f)), loot?.claimsForNextDrop())
  }

  @Test
  fun `a member playing another of its masters holds no claim`() {
    val a = master(A, MASTER_A)
    master(B, MASTER_B)
    sessions.activateSession(C, masterId = 99L, world.createEntity { id -> add(id, Account(C)) })

    val loot = claimsAfter(a to 10)

    assertEquals(setOf(A, B), loot?.claimsForNextDrop()?.single()?.accounts)
  }

  @Test
  fun `a kill no player hurt has nobody to hold its loot`() {
    val mob = world.createEntity { }

    assertNull(claimsAfter(mob to 10))
  }

  private companion object {
    const val PARTY = 5L
    const val A = 1L
    const val B = 2L
    const val C = 3L
    const val STRANGER = 4L
    const val MASTER_A = 11L
    const val MASTER_B = 12L
    const val MASTER_C = 13L
    const val MASTER_STRANGER = 14L
  }
}
