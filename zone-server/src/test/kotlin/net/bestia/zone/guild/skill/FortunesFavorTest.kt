package net.bestia.zone.guild.skill

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.skill.SkillId
import kotlin.test.Test
import kotlin.test.assertEquals

class FortunesFavorTest {

  private val world = testWorld()
  private val sessions = mockk<ConnectionInfoService>()
  private val sut = FortunesFavor(sessions, GuildConfig())

  @Test
  fun `level 5 turns a 10 percent drop into 12,5 percent`() {
    killerWith(GuildMembership(1, 5, skillLevels = mapOf(SkillId.FORTUNES_FAVOR.name to 5)))

    assertEquals(0.125, 0.10 * sut.factor(world, ACCOUNT), 1e-9)
  }

  @Test
  fun `a killer outside a guild, or in one without the skill, drops as always`() {
    killerWith(null)
    assertEquals(1.0, sut.factor(world, ACCOUNT))

    killerWith(GuildMembership(1, 5))
    assertEquals(1.0, sut.factor(world, ACCOUNT))
  }

  @Test
  fun `a killer who logged out drops as always`() {
    every { sessions.getSelectedMasterEntityId(ACCOUNT) } throws NoActiveSessionException(ACCOUNT)

    assertEquals(1.0, sut.factor(world, ACCOUNT))
  }

  private fun killerWith(membership: GuildMembership?) {
    val master = world.createEntity { id -> membership?.let { add(id, it) } }
    every { sessions.getSelectedMasterEntityId(ACCOUNT) } returns master
  }

  private companion object {
    const val ACCOUNT = 4L
  }
}
