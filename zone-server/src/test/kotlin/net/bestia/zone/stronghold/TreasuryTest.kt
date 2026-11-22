package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreasuryTest {

  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val plantedAt = Instant.parse("2026-11-10T08:00:00Z")
  private var stronghold = Stronghold(seed.rank, GUILD, State.STANDING, null, plantedAt, plantedAt)
    .withProgress(StrongholdTrack.ECONOMY, TrackProgress(5, 0))

  private fun treasury(now: Instant) = Treasury(
    strongholds = mockk {
      every { all() } answers { listOf(stronghold) }
      every { put(any()) } answers { stronghold = firstArg() }
    },
    seeds = mockk { every { awake() } returns listOf(seed) },
    lodes = mockk {
      every { kindsNear(seed, 3_000.0) } returns listOf(ResourceType.GOLD_LODE, ResourceType.IRON, ResourceType.TIN)
    },
    items = mockk<ItemTemplateRegistry> {
      every { idOf(any()) } answers { ITEM_IDS[firstArg()] }
    },
    config = StrongholdConfig(
      treasuryCraftingItems = listOf(TreasuryItem("star_core", fromEconomy = 8), TreasuryItem("drake_hide", fromEconomy = 2)),
    ),
    clock = Clock.fixed(now, ZoneOffset.UTC),
  )

  @Test
  fun `a drop each day after planting brings coins, the ore round the seed without gold, and crafting items`() {
    treasury(plantedAt.plus(Duration.ofHours(25))).makeDue()

    val drop = stronghold.drops.single()
    assertEquals(plantedAt.plus(Duration.ofHours(24)), drop.madeAt)
    assertEquals(60_000, drop.coins)
    assertEquals(mapOf(IRON_ORE to 10, TIN_ORE to 10, DRAKE_HIDE to 2), drop.goods)
  }

  @Test
  fun `at most three drops wait, the newest, even after a long break`() {
    treasury(plantedAt.plus(Duration.ofDays(6)).plusSeconds(1)).makeDue()

    assertEquals(6, stronghold.dropsMade)
    assertEquals((4..6L).map { plantedAt.plus(Duration.ofDays(it)) }, stronghold.drops.map { it.madeAt })
  }

  @Test
  fun `nothing is made before the day is up, and a site makes nothing`() {
    treasury(plantedAt.plus(Duration.ofHours(23))).makeDue()
    assertTrue(stronghold.drops.isEmpty())

    stronghold = stronghold.copy(state = State.SITE)
    treasury(plantedAt.plus(Duration.ofDays(2))).makeDue()
    assertTrue(stronghold.drops.isEmpty())
  }

  @Test
  fun `the next drop is a day after the last one made`() {
    val treasury = treasury(plantedAt.plus(Duration.ofHours(30)))
    treasury.makeDue()

    assertEquals(plantedAt.plus(Duration.ofHours(48)), treasury.nextDropAt(stronghold))
  }

  private companion object {
    const val GUILD = 1L
    const val IRON_ORE = 20L
    const val TIN_ORE = 21L
    const val GOLD_ORE = 22L
    const val DRAKE_HIDE = 30L
    val ITEM_IDS = mapOf("iron_ore" to IRON_ORE, "tin_ore" to TIN_ORE, "gold_ore" to GOLD_ORE, "drake_hide" to DRAKE_HIDE, "star_core" to 31L)
  }
}
