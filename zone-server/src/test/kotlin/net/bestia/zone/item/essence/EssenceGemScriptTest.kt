package net.bestia.zone.item.essence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EssenceGemScriptTest {

  private val world = testWorld()

  private val templates = mockk<ItemTemplateRegistry> {
    every { templateOf(SWORD) } returns template(SWORD, equipSlot = EquipmentSlot.RIGHT_HAND)
    every { templateOf(BOOTS) } returns template(BOOTS, equipSlot = EquipmentSlot.FOOTGEAR)
  }

  private val inventoryService = mockk<InventoryService>(relaxed = true)

  private val asyncJobExecutor = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val species = object : SpeciesEssences {
    override fun of(speciesId: Long) = EssenceDefinition(SocketGroup.WEAPON, EssenceEffect.BANE, "BEAST")
      .takeIf { speciesId == BOAR || speciesId == BOSS }

    override fun bossLevelOf(speciesId: Long) = BOSS_LEVEL.takeIf { speciesId == BOSS }
  }

  private val script = EssenceGemScript(templates, species, inventoryService, asyncJobExecutor)

  @Test
  fun `an essence goes into a free socket of its group and the gem is used up`() {
    val user = givenUser(gem(GemTier.RUBY), item(SWORD, uniqueId = 7, slots = 2, itemLevel = 30))

    assertTrue(script.executeHeld(world, user, targeting(7), GEM_ID))

    val sword = world.getOrThrow(user, Inventory::class).byPlayerItemId(7)!!
    assertEquals(listOf(Essence(BOAR, GemTier.RUBY)), sword.essences)
    verify { inventoryService.socket(MASTER_ID, 7, null, Essence(BOAR, GemTier.RUBY)) }
  }

  @Test
  fun `an essence does not fit another group`() {
    val user = givenUser(gem(GemTier.AMETHYST), item(BOOTS, uniqueId = 7, slots = 1))

    assertFalse(script.executeHeld(world, user, targeting(7), GEM_ID))
  }

  @Test
  fun `a full item takes no more essences`() {
    val full = item(SWORD, uniqueId = 7, slots = 1).apply { essences = listOf(Essence(BOAR, GemTier.AMETHYST)) }
    val user = givenUser(gem(GemTier.AMETHYST), full)

    assertFalse(script.executeHeld(world, user, targeting(7), GEM_ID))
  }

  /** No dormant state: a strong essence never sits half dead in a starter item. */
  @Test
  fun `an item below the gem's item level refuses the essence`() {
    val user = givenUser(gem(GemTier.RUBY), item(SWORD, uniqueId = 7, slots = 1, itemLevel = 20))

    assertFalse(script.executeHeld(world, user, targeting(7), GEM_ID))
  }

  @Test
  fun `worn gear takes an essence and its wearer's stats are worked out again`() {
    val user = givenUser(gem(GemTier.AMETHYST), item(SWORD, uniqueId = 7, slots = 1).apply { equipped = true })
    val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL).apply {
      equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD, uniqueId = 7, slots = 1))
    }
    world.add(user, equipment)

    assertTrue(script.executeHeld(world, user, targeting(7), GEM_ID))

    assertEquals(listOf(Essence(BOAR, GemTier.AMETHYST)), equipment.get(EquipmentSlot.RIGHT_HAND)!!.essences)
    assertTrue(world.has(user, IsStatusValueDirty::class))
  }

  @Test
  fun `a boss essence needs an item at most 20 levels below the boss`() {
    val user = givenUser(
      gem(GemTier.AMETHYST, species = BOSS),
      item(SWORD, uniqueId = 7, slots = 1, itemLevel = BOSS_LEVEL - 21),
      item(SWORD, uniqueId = 8, slots = 1, itemLevel = BOSS_LEVEL - 20)
    )

    assertFalse(script.executeHeld(world, user, targeting(7), GEM_ID))
    assertTrue(script.executeHeld(world, user, targeting(8), GEM_ID))
  }

  @Test
  fun `tools form their own group and plain materials have none`() {
    assertEquals(SocketGroup.TOOL, SocketGroup.of(template(1, toolKind = ToolKind.MINING)))
    assertEquals(SocketGroup.ACCESSORY, SocketGroup.of(template(2, equipSlot = EquipmentSlot.LIGHT_SOURCE)))
    assertNull(SocketGroup.of(template(3)))
  }

  private fun givenUser(vararg items: Inventory.Item) = world.createEntity { id ->
    add(id, Master(masterId = MASTER_ID))
    add(id, Inventory(items.toMutableList()))
  }

  private fun targeting(playerItemId: Long) = ScriptArgs.of(ScriptArgKeys.TARGET_ITEM to playerItemId)

  private fun gem(tier: GemTier, species: Long = BOAR) = Inventory.Item(
    itemId = EssenceGemScript.ITEM_ID, amount = 1, weight = 10, uniqueId = GEM_ID, stackable = false,
    heldEssence = Essence(species, tier)
  )

  private fun item(itemId: Long, uniqueId: Long, slots: Int, itemLevel: Int = 1) = Inventory.Item(
    itemId = itemId, amount = 1, weight = 1, uniqueId = uniqueId, stackable = false, slots = slots,
    itemLevel = itemLevel
  )

  private fun template(id: Long, equipSlot: EquipmentSlot? = null, toolKind: ToolKind? = null) =
    ItemTemplateRegistry.Template(
      id = id, baseLevel = 1, weight = 1, stackable = false, maxDurability = 100, material = ItemMaterial.CLOTH,
      toolKind = toolKind, equipSlot = equipSlot
    )

  private companion object {
    const val MASTER_ID = 42L
    const val BOAR = 6L
    const val BOSS = 120L
    const val BOSS_LEVEL = 54
    const val SWORD = 18L
    const val BOOTS = 40L
    const val GEM_ID = 900L
  }
}
