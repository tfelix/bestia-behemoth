package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.junit.jupiter.api.Test
import java.time.Instant.EPOCH
import kotlin.test.assertEquals

class StrongholdDisbandingTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = STANDING_RANK, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val standing = Stronghold(STANDING_RANK, GUILD, State.STANDING, null, EPOCH, EPOCH).copy(
    hasSteward = true, watchers = mapOf(WatcherKind.MELEE to 2), watcherStrength = 3,
  )
  private val site = Stronghold(SITE_RANK, GUILD, State.SITE, 40L, EPOCH, EPOCH)
  private val held = mutableListOf(standing, site)

  private val sites = mockk<EmpyriteSites>(relaxed = true)
  private val stewards = mockk<Stewards>(relaxed = true)
  private val watchers = mockk<Watchers>(relaxed = true)
  private val storages = mockk<GuildStorages>(relaxed = true)
  private val jobs = mockk<AsyncJobExecutor>(relaxed = true)
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val disbanding = StrongholdDisbanding(
    strongholds = mockk {
      every { heldBy(GUILD) } answers { held.filter { it.guildId == GUILD } }
      every { put(any()) } answers { held.removeIf { it.rank == firstArg<Stronghold>().rank }; held.add(firstArg()) }
    },
    seeds = mockk { every { awake() } returns listOf(seed) },
    sites = sites,
    stewards = stewards,
    watchers = watchers,
    storages = storages,
    storageRepository = mockk(relaxed = true),
    asyncJobExecutor = jobs,
    messages = messages,
  )

  @Test
  fun `a standing Stronghold stands empty, its steward and watchers gone, and everyone hears of it`() {
    val empyrite = world.createEntity { id ->
      add(id, Empyrite(STANDING_RANK))
      add(id, GuildOwned(GUILD))
    }

    disbanding.disbanded(world, GUILD, "Iron Wolves")

    assertEquals(
      standing.copy(guildId = Stronghold.UNHELD, hasSteward = false, watchers = emptyMap(), watcherStrength = 0),
      held.single { it.rank == STANDING_RANK },
    )
    assertEquals(GuildOwned(Stronghold.UNHELD), world.get(empyrite, GuildOwned::class))
    verify { stewards.dismiss(world, STANDING_RANK) }
    verify { watchers.dismissAll(world, STANDING_RANK) }
    verify {
      messages.sendToAllConnected(AnnouncementSMSG(Announcement.STRONGHOLD_UNHELD, listOf("Iron Wolves", "Hochvein")))
    }
  }

  @Test
  fun `an unfinished site is simply gone, and so is the empty storage`() {
    disbanding.disbanded(world, GUILD, "Iron Wolves")

    verify { sites.abandon(world, site) }
    verify { storages.forget(GUILD) }
    verify { jobs.submit(ItemHolder.GuildStorage(GUILD), any()) }
  }

  private companion object {
    const val GUILD = 1L
    const val STANDING_RANK = 2
    const val SITE_RANK = 3
  }
}
