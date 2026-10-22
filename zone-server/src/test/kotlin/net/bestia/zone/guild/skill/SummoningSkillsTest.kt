package net.bestia.zone.guild.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildMembership
import kotlin.test.Test
import kotlin.test.assertEquals

/** Emergency Recall and Rally to the Banner: who they move, where to, and when they refuse. */
class SummoningSkillsTest {

  private val tree = GuildSkillTree().apply {
    load(
      listOf(
        GuildSkillNode(skillId = RECALL_ID, identifier = "EMERGENCY_RECALL", maxLevel = 1),
        GuildSkillNode(skillId = RALLY_ID, identifier = "RALLY_TO_THE_BANNER", maxLevel = 1),
      )
    )
  }
  private val cooldowns = GuildSkillCooldowns()
  private val banners = WarBanners()
  private val recall = EmergencyRecall(tree, cooldowns, NoGuildStrongholds())
  private val rally = RallyToTheBanner(tree, cooldowns, banners)

  private val membership = GuildMembership(GUILD, taxPercent = 5, mayUseGuildSkills = true)
  private val ecs = testWorld()
  private val caster = ecs.createEntity { id -> add(id, membership) }

  @Test
  fun `Emergency Recall is refused outside the guild's own Stronghold, which no guild has yet`() {
    assertEquals(OpError.GUILD_NOT_IN_STRONGHOLD, recall.checkCastStart(ecs, caster, 1))
  }

  @Test
  fun `Rally to the Banner needs a banner standing`() {
    assertEquals(OpError.GUILD_NO_BANNER, rally.checkCastStart(ecs, caster, 1))

    banners.plant(GUILD, WarBanners.Planted(entityId = 99, position = Vec3L(40, 50, 0)))

    assertEquals(null, rally.checkCastStart(ecs, caster, 1))
  }

  @Test
  fun `Rally to the Banner calls the whole guild to the banner, the caster too`() {
    banners.plant(GUILD, WarBanners.Planted(entityId = 99, position = Vec3L(40, 50, 0)))
    val world = RecordingSkillWorld().putMembership(BattleContextFixture.ATTACKER_ID, membership)

    rally.execute(SkillContextFixture.skillCtx(world = world, skillId = RALLY_ID, skillLevel = 1))

    assertEquals(RecordingSkillWorld.Summon(GUILD, Vec3L(40, 50, 0), includeCaster = true), world.summons.single())
  }

  @Test
  fun `the two do not share a cooldown`() {
    banners.plant(GUILD, WarBanners.Planted(entityId = 99, position = Vec3L(40, 50, 0)))
    cooldowns.tryStart(GUILD, RECALL_ID, cooldownMillis = 300_000)

    assertEquals(null, rally.checkCastStart(ecs, caster, 1))
  }

  private companion object {
    const val GUILD = 1L
    const val RECALL_ID = 9020L
    const val RALLY_ID = 9021L
  }
}
