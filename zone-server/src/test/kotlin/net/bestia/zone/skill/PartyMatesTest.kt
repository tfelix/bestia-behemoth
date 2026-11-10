package net.bestia.zone.skill

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PartyMatesTest {

  private val world = testWorld()

  @Test
  fun `a party skill reaches members in range and their bestias, not strangers or the far away`() {
    val party = setOf(PartyMembership.Member(1L, 1L), PartyMembership.Member(2L, 2L))
    val caster = master(1L, at = Vec3L(0, 0, 0), party = party)
    val friend = master(2L, at = Vec3L(10, 0, 0), party = party)
    val friendsBestia = bestia(ownerMasterId = 2L, at = Vec3L(12, 0, 0))
    master(3L, at = Vec3L(5, 0, 0))
    master(2L, at = Vec3L(200, 0, 0), party = party)

    val reached = PartyMates.near(world, caster, Vec3L(0, 0, 0))

    assertEquals(setOf(caster, friend, friendsBestia), reached.toSet())
  }

  @Test
  fun `without a party only the caster and its own bestias are reached`() {
    val caster = master(1L, at = Vec3L(0, 0, 0))
    val own = bestia(ownerMasterId = 1L, at = Vec3L(3, 0, 0))
    master(3L, at = Vec3L(5, 0, 0))

    assertEquals(setOf(caster, own), PartyMates.near(world, caster, Vec3L(0, 0, 0)).toSet())
  }

  private fun master(masterId: Long, at: Vec3L, party: Set<PartyMembership.Member>? = null): EntityId {
    return world.createEntity { id ->
      add(id, Account(masterId))
      add(id, Master(masterId))
      add(id, Position.fromVec3(at))
      party?.let { add(id, PartyMembership(partyId = 1L, members = it, expShare = PartyExpShare.entries.first())) }
    }
  }

  private fun bestia(ownerMasterId: Long, at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Account(ownerMasterId))
      add(id, OwnedBestia(ownerMasterId, playerBestiaId = 100L + id))
      add(id, Position.fromVec3(at))
    }
  }
}
