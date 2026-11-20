package net.bestia.zone.crafting.repair

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.workexp.WorkExpService
import org.junit.jupiter.api.Test

class KitSalvageTest {

  private val world = testWorld()
  private val loot = mockk<LootItemEntitySpawner>(relaxed = true)
  private val workExp = mockk<WorkExpService>(relaxed = true) {
    every { reads } returns emptySet()
    every { writes } returns emptySet()
  }
  private val kits = mockk<KitCatalogue> {
    every { repairCostOf(StaticEntityKind.FORGE) } returns RepairCost(maxHp = 500, buildSeconds = 20f, materials = mapOf(PLANK to 10, IRON to 3))
  }

  private val salvage = KitSalvage(kits, loot, workExp)

  @Test
  fun `a quarter of the kit's materials drop for the salvager, rounded down`() {
    salvage.salvage(world, forge(), AT, SALVAGER, SALVAGER_ACCOUNT)

    verify { loot.spawnLootItem(world, itemId = PLANK, amount = 2, pos = AT, lootOwner = SALVAGER_ACCOUNT) }
    verify(exactly = 0) { loot.spawnLootItem(world, itemId = IRON, amount = any(), pos = any(), lootOwner = any()) }
  }

  @Test
  fun `the salvager earns work experience for a quarter of the build time`() {
    salvage.salvage(world, forge(), AT, SALVAGER, SALVAGER_ACCOUNT)

    verify { workExp.award(world, SALVAGER, 5.0) }
  }

  private fun forge(): StructureEntry {
    return StructureEntry(1L, StaticEntityKind.FORGE, 3L, 7L, AT, 0f)
  }

  private companion object {
    const val PLANK = 10L
    const val IRON = 11L
    const val SALVAGER = 99L
    const val SALVAGER_ACCOUNT = 8L
    val AT = Vec3L(1, 2, 3)
  }
}
