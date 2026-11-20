package net.bestia.zone.prop.ecs.construction

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteWorkersTest {

  private val world = testWorld()

  @Test
  fun `anybody may help with a player's site`() {
    val site = world.createEntity { }
    val stranger = world.createEntity { id -> add(id, Account(9L)) }

    assertTrue(SiteWorkers.mayWork(world, stranger, site))
  }

  @Test
  fun `only the guild and its members' bestias work on a guild's site`() {
    val site = world.createEntity { id -> add(id, GuildOwned(GUILD)) }
    val member = world.createEntity { id ->
      add(id, Account(7L))
      add(id, GuildMembership(GUILD, taxPercent = 5))
    }
    val membersBestia = world.createEntity { id -> add(id, Account(7L)) }
    val outsider = world.createEntity { id ->
      add(id, Account(8L))
      add(id, GuildMembership(OTHER_GUILD, taxPercent = 5))
    }

    assertTrue(SiteWorkers.mayWork(world, member, site))
    assertTrue(SiteWorkers.mayWork(world, membersBestia, site))
    assertFalse(SiteWorkers.mayWork(world, outsider, site))
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
