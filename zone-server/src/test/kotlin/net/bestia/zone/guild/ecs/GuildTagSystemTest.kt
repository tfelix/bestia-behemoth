package net.bestia.zone.guild.ecs

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuildTagSystemTest {

  private val world = testWorld(systems = listOf(GuildTagSystem()))

  private val membership = GuildMembership(guildId = 7, taxPercent = 5, guildName = "Moonlit Order", rankTitle = "Officer")

  @Test
  fun `a member and its bestia carry the guild's name and the member's rank`() {
    val master = world.createEntity { id ->
      add(id, Master(1))
      add(id, membership)
    }
    val bestia = world.createEntity { id -> add(id, OwnedBestia(masterId = 1, playerBestiaId = 3L)) }

    pulse()

    val tag = GuildTag(7, "Moonlit Order", "Officer", emblemHash = "")
    assertEquals(tag, world.read { get(master, GuildTag::class) })
    assertEquals(tag, world.read { get(bestia, GuildTag::class) })
  }

  @Test
  fun `a new rank title follows, and leaving the guild takes the tag away`() {
    val master = world.createEntity { id ->
      add(id, Master(1))
      add(id, membership)
    }
    pulse()

    world.modify(master) { add(it, membership.copy(rankTitle = "Herald")) }
    pulse()
    assertEquals("Herald", world.read { get(master, GuildTag::class)?.rankTitle })

    world.modify(master) { remove(it, GuildMembership::class) }
    pulse()
    assertNull(world.read { get(master, GuildTag::class) })
  }

  private fun pulse() {
    repeat(2) { world.tick(1f) }
  }
}
