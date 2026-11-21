package net.bestia.zone.ai.perception

import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.ClosedToAttack
import net.bestia.zone.entity.ecs.Poi
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostilityTest {

  private val world = testWorld()

  private val wild = world.createEntity { }
  private val master = world.createEntity { world.add(it, Master(1L)) }
  private val bestia = ownedBestia(masterId = 1L)
  private val strangersBestia = ownedBestia(masterId = 2L)

  private fun ownedBestia(masterId: Long): EntityId {
    return world.createEntity { world.add(it, OwnedBestia(masterId = masterId, playerBestiaId = it)) }
  }

  @Test
  fun `the wild hunts masters and what they own`() {
    assertTrue(Hostility.isHostile(world, wild, master))
    assertTrue(Hostility.isHostile(world, wild, bestia))
  }

  @Test
  fun `a bestia sees masters as enemies but not other bestias`() {
    assertTrue(Hostility.isHostile(world, bestia, master))
    assertFalse(Hostility.isHostile(world, bestia, strangersBestia))
  }

  @Test
  fun `nobody hunts the wild on sight`() {
    assertFalse(Hostility.isHostile(world, bestia, wild))
    assertFalse(Hostility.isHostile(world, wild, wild))
  }

  @Test
  fun `a controlled creature turns on the wild and spares masters`() {
    val thrall = world.createEntity { world.add(it, controlled()) }

    assertTrue(Hostility.isHostile(world, thrall, wild))
    assertFalse(Hostility.isHostile(world, thrall, master))
    assertTrue(Hostility.isHostile(world, wild, thrall))
  }

  @Test
  fun `no creature hunts a POI, not even a controlled one or a guard`() {
    val poi = world.createEntity { world.add(it, Poi(PoiKind.BROKEN_OBELISK, instanceId = 0)) }
    val thrall = world.createEntity { world.add(it, controlled()) }

    assertFalse(Hostility.isHostile(world, thrall, poi))
    assertFalse(Hostility.isHostile(world, wild, poi))
    assertFalse(Hostility.isIntruder(world, bestia, poi))
  }

  @Test
  fun `a guild's watcher drives out only rivals with Approval and their bestias, and only while open to attack`() {
    val watcher = world.createEntity { world.add(it, GuildOwned(GUILD)) }
    val rival = guildMember(OTHER_GUILD, accountId = 8L, approved = true)
    val rivalsBestia = world.createEntity { world.add(it, Account(8L)) }
    val unapproved = guildMember(THIRD_GUILD, accountId = 9L, approved = false)
    val ownMember = guildMember(GUILD, accountId = 10L, approved = true)

    assertTrue(Hostility.isIntruder(world, watcher, rival))
    assertTrue(Hostility.isIntruder(world, watcher, rivalsBestia))
    assertFalse(Hostility.isIntruder(world, watcher, unapproved))
    assertFalse(Hostility.isIntruder(world, watcher, ownMember))
    assertFalse(Hostility.isIntruder(world, watcher, master), "a guildless master is left in peace")
    assertFalse(Hostility.isIntruder(world, watcher, wild))

    world.add(watcher, ClosedToAttack)
    assertFalse(Hostility.isIntruder(world, watcher, rival))
  }

  private fun guildMember(guildId: Long, accountId: Long, approved: Boolean): EntityId {
    val skills = if (approved) mapOf(SkillId.OFFICIAL_GUILD_APPROVAL.name to 1) else emptyMap()

    return world.createEntity {
      world.add(it, Master(accountId))
      world.add(it, Account(accountId))
      world.add(it, GuildMembership(guildId, taxPercent = 5, skillLevels = skills))
    }
  }

  private fun controlled(): StatusEffects {
    return StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.CONTROLLED.id, level = 1, remainingSeconds = 30f)))
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val THIRD_GUILD = 3L
  }
}
