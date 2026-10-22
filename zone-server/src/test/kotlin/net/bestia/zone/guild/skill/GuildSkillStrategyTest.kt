package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.guild.GuildSkillCooldownSMSG
import net.bestia.zone.identity.ecs.GuildMembership
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Through Battle Orders, which is the plainest of the guild casts. */
class GuildSkillStrategyTest {

  private val tree = GuildSkillTree().apply {
    load(listOf(GuildSkillNode(skillId = BATTLE_ORDERS_ID, identifier = "BATTLE_ORDERS", maxLevel = 1)))
  }
  private val cooldowns = GuildSkillCooldowns()
  private val sut = BattleOrders(tree, cooldowns)

  private val caster = BattleContextFixture.ATTACKER_ID
  private val membership = GuildMembership(GUILD, taxPercent = 5, mayUseGuildSkills = true)

  @Test
  fun `the cast buffs the guild around the caster and tells every member the cooldown`() {
    val world = RecordingSkillWorld().putPosition(caster, Vec3L(10, 20, 0)).putMembership(caster, membership)

    sut.execute(SkillContextFixture.skillCtx(world = world, skillId = BATTLE_ORDERS_ID, skillLevel = 1))

    assertEquals(
      RecordingSkillWorld.GuildEffect(GUILD, Vec3L(10, 20, 0), 30, StatusEffectId.BATTLE_ORDERS.id, 1),
      world.guildEffects.single(),
    )
    assertEquals(GUILD to GuildSkillCooldownSMSG(BATTLE_ORDERS_ID, 180_000), world.guildMessages.single())
  }

  @Test
  fun `once cast it recovers for the whole guild, not for other guilds`() {
    val world = RecordingSkillWorld().putPosition(caster, Vec3L(0, 0, 0)).putMembership(caster, membership)
    sut.execute(SkillContextFixture.skillCtx(world = world, skillId = BATTLE_ORDERS_ID, skillLevel = 1))

    val ecs = testWorld()
    val guildmate = ecs.createEntity { id -> add(id, membership) }
    val stranger = ecs.createEntity { id -> add(id, GuildMembership(OTHER_GUILD, taxPercent = 5)) }

    assertEquals(OpError.GUILD_SKILL_COOLDOWN, sut.checkCastStart(ecs, guildmate, 1))
    assertNull(sut.checkCastStart(ecs, stranger, 1))
  }

  @Test
  fun `two casts landing in the same tick buff the guild once`() {
    val world = RecordingSkillWorld().putPosition(caster, Vec3L(0, 0, 0)).putMembership(caster, membership)

    repeat(2) { sut.execute(SkillContextFixture.skillCtx(world = world, skillId = BATTLE_ORDERS_ID, skillLevel = 1)) }

    assertEquals(1, world.guildEffects.size)
  }

  @Test
  fun `a caster outside any guild is refused at the start`() {
    val ecs = testWorld()
    val loner = ecs.createEntity { }

    assertEquals(OpError.GUILD_NOT_A_MEMBER, sut.checkCastStart(ecs, loner, 1))
  }

  @Test
  fun `the cooldown runs out`() {
    assertTrue(cooldowns.tryStart(GUILD, BATTLE_ORDERS_ID, cooldownMillis = 1_000, now = 0))
    assertEquals(400, cooldowns.remainingMillis(GUILD, BATTLE_ORDERS_ID, now = 600))
    assertTrue(cooldowns.tryStart(GUILD, BATTLE_ORDERS_ID, cooldownMillis = 1_000, now = 1_000))
  }

  private companion object {
    const val BATTLE_ORDERS_ID = 9017L
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
