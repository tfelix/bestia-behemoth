package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.guild.GuildNoticeSMSG.Notice
import net.bestia.zone.guild.GuildService
import net.bestia.zone.guild.persistence.GuildLogKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class StrongholdNewsTest {

  private val world = testWorld()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val guilds = mockk<GuildService>(relaxed = true)
  private var now = Instant.parse("2026-11-22T12:00:00Z")

  private val news = StrongholdNews(
    seeds = mockk { every { awake() } returns listOf(EmpyriteSeed(rank = 2, x = 0.0, y = 0.0, name = "Hochvein", settlement = -1)) },
    messages = messages,
    guilds = guilds,
    // Runs the log write on the calling thread.
    asyncJobExecutor = mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    clock = object : Clock() {
      override fun instant() = now
      override fun getZone(): ZoneId = ZoneId.of("UTC")
      override fun withZone(zone: ZoneId?) = this
    },
  )

  @Test
  fun `a capture is news for both guilds' members being played, and goes into both logs`() {
    member(GUILD, account = 7L)
    member(OTHER_GUILD, account = 8L)
    member(THIRD_GUILD, account = 9L)

    news.captured(world, newGuildId = OTHER_GUILD, oldGuildId = GUILD, rank = 2)

    verify { messages.sendToPlayer(8L, GuildNoticeSMSG(Notice.STRONGHOLD_CAPTURED, listOf("Hochvein"))) }
    verify { messages.sendToPlayer(7L, GuildNoticeSMSG(Notice.STRONGHOLD_LOST, listOf("Hochvein"))) }
    verify(exactly = 0) { messages.sendToPlayer(9L, any<GuildNoticeSMSG>()) }
    verify { guilds.logStronghold(OTHER_GUILD, GuildLogKind.STRONGHOLD_CAPTURED, "", "Hochvein") }
    verify { guilds.logStronghold(GUILD, GuildLogKind.STRONGHOLD_LOST, "", "Hochvein") }
  }

  @Test
  fun `an attack is told once, and again only after five quiet minutes`() {
    member(GUILD, account = 7L)

    news.underAttack(world, GUILD, 2)
    now = now.plusSeconds(60)
    news.underAttack(world, GUILD, 2)
    now = now.plusSeconds(5 * 60)
    news.underAttack(world, GUILD, 2)

    verify(exactly = 2) { messages.sendToPlayer(7L, GuildNoticeSMSG(Notice.EMPYRITE_UNDER_ATTACK, listOf("Hochvein"))) }
  }

  private fun member(guildId: Long, account: Long) {
    world.createEntity { id ->
      add(id, Account(account))
      add(id, GuildMembership(guildId, taxPercent = 5))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val THIRD_GUILD = 3L
  }
}
