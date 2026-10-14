package net.bestia.zone.aoi

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityAudienceTest {

  private val world = testWorld()
  private val visibility = RecordingEntityVisibility()
  private val sessions = ConnectionInfoService()
  private val sut = EntityAudience(visibility, sessions)

  private fun masterEntity(masterId: Long): EntityId {
    return world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, Master(masterId))
    }
  }

  private fun bestiaOf(masterId: Long): EntityId {
    return world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, OwnedBestia(masterId, playerBestiaId = 21L))
    }
  }

  @Test
  fun `the player hears about their selected master out of sight`() {
    val master = masterEntity(MASTER_ID)
    sessions.activateSession(OWNER, MASTER_ID, master)

    assertEquals(setOf(OWNER), sut.of(world, master))
  }

  @Test
  fun `the player hears about a bestia of their selected master out of sight`() {
    sessions.activateSession(OWNER, MASTER_ID, masterEntity(MASTER_ID))
    val bestia = bestiaOf(MASTER_ID)
    visibility.observers[bestia] = setOf(WATCHER)

    assertEquals(setOf(WATCHER, OWNER), sut.of(world, bestia))
  }

  @Test
  fun `a bestia of the account's other master stays out of sight`() {
    sessions.activateSession(OWNER, MASTER_ID, masterEntity(MASTER_ID))
    val bestia = bestiaOf(OTHER_MASTER_ID)

    assertEquals(emptySet(), sut.of(world, bestia))
  }

  @Test
  fun `a bestia of a player who logged out stays out of sight`() {
    val bestia = bestiaOf(MASTER_ID)

    assertEquals(emptySet(), sut.of(world, bestia))
  }

  @Test
  fun `a mob only reaches those who see it`() {
    val mob = world.createEntity { }
    visibility.observers[mob] = setOf(WATCHER)

    assertEquals(setOf(WATCHER), sut.of(world, mob))
  }

  private companion object {
    const val OWNER = 1L
    const val WATCHER = 2L
    const val MASTER_ID = 7L
    const val OTHER_MASTER_ID = 8L
  }
}
