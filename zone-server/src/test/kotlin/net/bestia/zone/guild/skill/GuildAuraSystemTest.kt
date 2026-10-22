package net.bestia.zone.guild.skill

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildAuraSystemTest {

  private val catalogue = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val aura = GuildAura()
  private val statusEffects = StatusEffectService(catalogue, StatusEffectScriptRegistry(listOf(aura)))
  private val world = testWorld(systems = listOf(GuildAuraSystem(statusEffects, GuildConfig())))

  private val auraLevels = mapOf(SkillId.GREAT_LEADERSHIP.name to 3, SkillId.STEADFAST_MIND.name to 2)

  @Test
  fun `members within 15 m of the leader get the aura, those further away do not`() {
    val leader = master(1, GUILD, x = 0, isLeader = true, levels = auraLevels)
    val near = master(2, GUILD, x = 15)
    val far = master(3, GUILD, x = 16)

    pulse()

    assertTrue(hasAura(leader))
    assertTrue(hasAura(near))
    assertFalse(hasAura(far))
  }

  @Test
  fun `the bestia a member plays gets the aura too`() {
    master(1, GUILD, x = 0, isLeader = true, levels = auraLevels)
    master(2, GUILD, x = 200)
    val bestia = world.createEntity { id ->
      add(id, Account(2))
      add(id, OwnedBestia(masterId = 2, playerBestiaId = 9L))
      add(id, Position(5, 0, 0))
    }

    pulse()

    assertTrue(hasAura(bestia))
  }

  @Test
  fun `the aura follows the bestia the leader plays`() {
    master(1, GUILD, x = 0, isLeader = true, levels = auraLevels, playing = false)
    world.createEntity { id ->
      add(id, Account(1))
      add(id, OwnedBestia(masterId = 1, playerBestiaId = 8L))
      add(id, ActivePlayer)
      add(id, Position(100, 0, 0))
    }
    val nearBestia = master(2, GUILD, x = 110)
    val nearMaster = master(3, GUILD, x = 5)

    pulse()

    assertTrue(hasAura(nearBestia))
    assertFalse(hasAura(nearMaster))
  }

  @Test
  fun `no aura from another guild's leader, nor from a leader whose guild learned none`() {
    master(1, GUILD, x = 0, isLeader = true, levels = emptyMap())
    master(2, OTHER_GUILD, x = 0, isLeader = true, levels = auraLevels)
    val member = master(3, GUILD, x = 1)

    pulse()

    assertFalse(hasAura(member))
  }

  @Test
  fun `the aura adds the leader's guild's levels`() {
    val leader = master(1, GUILD, x = 0, isLeader = true, levels = auraLevels)
    val context = StatusValueRecalcContext(StatusValues(10, 10, 10, 10, 10, 10), baseSpeed = 1f)

    aura.apply(world, entityId = 99, context, level = 1, sourceEntityId = leader)

    assertEquals(13, context.strength)
    assertEquals(12, context.willpower)
    assertEquals(10, context.dexterity)
  }

  private fun master(
    masterId: Long,
    guildId: Long,
    x: Long,
    isLeader: Boolean = false,
    levels: Map<String, Int> = emptyMap(),
    playing: Boolean = true,
  ): EntityId {
    return world.createEntity { id ->
      add(id, Account(masterId))
      add(id, Master(masterId))
      add(id, Position(x, 0, 0))
      add(id, GuildMembership(guildId, taxPercent = 5, isLeader = isLeader, skillLevels = levels))
      if (playing) {
        add(id, ActivePlayer)
      }
    }
  }

  private fun hasAura(id: EntityId): Boolean {
    return world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.GUILD_AURA.id) ?: false
  }

  private fun pulse() {
    repeat(2) { world.tick(1f) }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
