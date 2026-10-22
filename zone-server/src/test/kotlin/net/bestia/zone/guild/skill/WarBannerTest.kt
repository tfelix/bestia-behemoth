package net.bestia.zone.guild.skill

import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Planting a banner with the item, and the system taking it down again. */
class WarBannerTest {

  private val banners = WarBanners()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val config = GuildConfig(warBannerSeconds = 10)
  private val script = WarBannerScript(banners, config, messages)
  private val world = testWorld(systems = listOf(WarBannerSystem(banners, messages)))

  @Test
  fun `a member with Rally to the Banner plants it, owned by the guild`() {
    val member = member(rally = 1)

    assertTrue(script.execute(world, member, at(Vec3L(3, 0, 0))))

    val banner = assertNotNull(banners.of(GUILD))
    assertEquals(Vec3L(3, 0, 0), banner.position)
    assertEquals(GUILD, world.get(banner.entityId, GuildOwned::class)?.guildId)
    verify { messages.sendToPlayer(ACCOUNT, GuildNoticeSMSG(GuildNoticeSMSG.Notice.BANNER_PLANTED)) }
  }

  @Test
  fun `a new banner takes the old one down`() {
    val member = member(rally = 1)
    script.execute(world, member, at(Vec3L(1, 0, 0)))
    val first = assertNotNull(banners.of(GUILD)).entityId

    script.execute(world, member, at(Vec3L(2, 0, 0)))

    assertFalse(world.isAlive(first))
    assertEquals(Vec3L(2, 0, 0), banners.of(GUILD)?.position)
  }

  @Test
  fun `without the skill, or out of reach, the banner stays in the bag`() {
    assertFalse(script.execute(world, member(rally = 0), at(Vec3L(1, 0, 0))))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.GUILD_BANNER_NOT_ALLOWED)) }

    assertFalse(script.execute(world, member(rally = 1), at(Vec3L(50, 0, 0))))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.GUILD_BANNER_OUT_OF_RANGE)) }
    assertNull(banners.of(GUILD))
  }

  @Test
  fun `a banner fades when its time runs out`() {
    script.execute(world, member(rally = 1), at(Vec3L(1, 0, 0)))
    val banner = assertNotNull(banners.of(GUILD)).entityId

    repeat(11) { world.tick(1f) }

    assertFalse(world.isAlive(banner))
    assertNull(banners.of(GUILD))
    verify { messages.sendToPlayer(ACCOUNT, GuildNoticeSMSG(GuildNoticeSMSG.Notice.BANNER_FADED)) }
  }

  @Test
  fun `a banner cut down tells the guild`() {
    script.execute(world, member(rally = 1), at(Vec3L(1, 0, 0)))
    val banner = assertNotNull(banners.of(GUILD)).entityId

    world.add(banner, Dead())
    world.tick(0.1f)

    assertNull(banners.of(GUILD))
    verify { messages.sendToPlayer(ACCOUNT, GuildNoticeSMSG(GuildNoticeSMSG.Notice.BANNER_FALLEN)) }
  }

  private fun member(rally: Int): EntityId {
    return world.createEntity { id ->
      add(id, Account(ACCOUNT))
      add(id, Master(1))
      add(id, ActivePlayer)
      add(id, Position(0, 0, 0))
      add(
        id,
        GuildMembership(
          GUILD,
          taxPercent = 5,
          mayUseGuildSkills = true,
          skillLevels = mapOf(SkillId.RALLY_TO_THE_BANNER.name to rally),
        ),
      )
    }
  }

  private fun at(position: Vec3L): ScriptArgs {
    return ScriptArgs.of(ScriptArgKeys.POSITION to position)
  }

  private companion object {
    const val GUILD = 1L
    const val ACCOUNT = 4L
  }
}
