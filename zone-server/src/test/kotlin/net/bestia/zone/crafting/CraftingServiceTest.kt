package net.bestia.zone.crafting

import io.mockk.every
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.CraftingFixture.Companion.INPUT_ITEM
import net.bestia.zone.crafting.CraftingFixture.Companion.MASTER_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.OUTPUT_ITEM
import net.bestia.zone.crafting.CraftingFixture.Companion.SKILL_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.TARGET_ITEM
import net.bestia.zone.crafting.CraftingFixture.Companion.instance
import net.bestia.zone.crafting.CraftingFixture.Companion.recipe
import net.bestia.zone.crafting.CraftingFixture.Companion.stack
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.crafting.Crafting
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.world.prop.StaticEntityKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rules a craft is made of: who may start one, what it costs, and what happens when it lands.
 *
 * Recipes here are built at `baseSuccessChance = 1` or `0` rather than mocking the roll, so a test says "this
 * always succeeds" in the same language the catalogue does.
 */
class CraftingServiceTest {

  private val produce = recipe(id = 1, identifier = "PRODUCE_ONE")

  @Test
  fun `a craft that has its materials starts and spends nothing yet`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))

    assertNull(fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0))

    val crafting = fixture.world.get(crafter, Crafting::class)
    assertNotNull(crafting)
    assertEquals(produce.craftSeconds, crafting!!.totalSeconds)

    // The whole point of consuming late: a cancelled craft has nothing to refund.
    assertEquals(5, fixture.inventoryOf(crafter).getItems().single { it.itemId == INPUT_ITEM }.amount)
  }

  @Test
  fun `a craft with nothing to make it from is refused before it starts`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 1)), knownSkills = mapOf(SKILL_ID to 1))

    assertEquals(
      OpError.CRAFT_MISSING_MATERIALS,
      fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0)
    )
    assertNull(fixture.world.get(crafter, Crafting::class))
  }

  @Test
  fun `a crafter who has not learned the skill is refused`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)))

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0)
    )
  }

  /** Every crafting skill is a master skill, and the durable half of a craft goes through the master container. */
  @Test
  fun `an entity that is not a master cannot craft`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))
    fixture.world.remove(crafter, Master::class)

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0)
    )
  }

  @Test
  fun `a second craft is refused while one is running`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 9)), knownSkills = mapOf(SKILL_ID to 1))

    assertNull(fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0))
    assertEquals(
      OpError.CRAFT_ALREADY_IN_PROGRESS,
      fixture.service.start(fixture.world, crafter, produce.id, targetUniqueId = 0)
    )
  }

  @Test
  fun `a recipe needing a station is refused with none in range and allowed with one`() {
    val atForge = recipe(id = 2, identifier = "AT_FORGE", station = StaticEntityKind.FORGE)

    val without = CraftingFixture(listOf(atForge))
    val one = without.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))
    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      without.service.start(without.world, one, atForge.id, targetUniqueId = 0)
    )

    val with = CraftingFixture(listOf(atForge)).withStation(StaticEntityKind.FORGE)
    val other = with.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))
    assertNull(with.service.start(with.world, other, atForge.id, targetUniqueId = 0))
  }

  @Test
  fun `resolving a successful craft spends the inputs and queues the grant`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))

    fixture.service.resolve(fixture.world, crafter, crafting(produce.id))

    assertEquals(3, fixture.inventoryOf(crafter).getItems().single { it.itemId == INPUT_ITEM }.amount)

    val intent = fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class)
    assertNotNull(intent, "the crafted item goes through the one grant path that checks carry capacity")
    assertEquals(OUTPUT_ITEM, intent!!.itemId)

    // Durable removal is handed to the executor keyed on the master, never done inline on the tick thread.
    verify { fixture.inventoryService.consumeAll(MASTER_ID, listOf(INPUT_ITEM to 2)) }
  }

  /** The whole risk in a craft, and what makes the success tables in the design docs worth anything. */
  @Test
  fun `a failed craft still spends the inputs and produces nothing`() {
    val doomed = recipe(id = 3, identifier = "DOOMED", baseSuccessChance = 0f)
    val fixture = CraftingFixture(listOf(doomed))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))

    fixture.service.resolve(fixture.world, crafter, crafting(doomed.id))

    assertEquals(3, fixture.inventoryOf(crafter).getItems().single { it.itemId == INPUT_ITEM }.amount)
    assertNull(fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class))
  }

  @Test
  fun `a craft whose materials vanished while it ran spends nothing and refuses`() {
    val fixture = CraftingFixture(listOf(produce))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))

    // What dropping the pile mid-craft looks like from here.
    fixture.inventoryOf(crafter).removeItem(INPUT_ITEM)

    fixture.service.resolve(fixture.world, crafter, crafting(produce.id))

    assertNull(fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class))
    verify(exactly = 0) { fixture.inventoryService.consumeAll(any(), any()) }
  }

  @Test
  fun `a repair puts the item back to full and mirrors it durably`() {
    val repair = recipe(id = 4, identifier = "REPAIR", effect = RecipeEffect.REPAIR)
    val fixture = CraftingFixture(listOf(repair))
    val crafter = fixture.givenCrafter(
      items = listOf(stack(INPUT_ITEM, 5), instance(TARGET_ITEM, uniqueId = 77, durability = 12, maxDurability = 200)),
      knownSkills = mapOf(SKILL_ID to 1)
    )

    fixture.service.resolve(fixture.world, crafter, crafting(repair.id, targetUniqueId = 77))

    assertEquals(200, fixture.inventoryOf(crafter).getItems().single { it.uniqueId == 77L }.durability)
    verify { fixture.inventoryService.updateInstance(MASTER_ID, 77L, any()) }
  }

  @Test
  fun `a repair is refused on an item that is already whole and on one that cannot wear`() {
    val repair = recipe(id = 5, identifier = "REPAIR", effect = RecipeEffect.REPAIR)
    val fixture = CraftingFixture(listOf(repair))
    val crafter = fixture.givenCrafter(
      items = listOf(
        stack(INPUT_ITEM, 9),
        instance(TARGET_ITEM, uniqueId = 1, durability = 200, maxDurability = 200),
        instance(TARGET_ITEM, uniqueId = 2)
      ),
      knownSkills = mapOf(SKILL_ID to 1)
    )

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 1)
    )
    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 2)
    )
  }

  /** You take gear off to work on it - the same rule the container applies to dropping and consuming. */
  @Test
  fun `a worn item is not a legal target`() {
    val repair = recipe(id = 6, identifier = "REPAIR", effect = RecipeEffect.REPAIR)
    val fixture = CraftingFixture(listOf(repair))
    val crafter = fixture.givenCrafter(
      items = listOf(
        stack(INPUT_ITEM, 5),
        instance(TARGET_ITEM, uniqueId = 88, durability = 3, maxDurability = 200, equipped = true)
      ),
      knownSkills = mapOf(SKILL_ID to 1)
    )

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 88)
    )
  }

  @Test
  fun `an upgrade raises the level by one and stops at the cap`() {
    val upgrade = recipe(id = 7, identifier = "UPGRADE", effect = RecipeEffect.UPGRADE)
    val fixture = CraftingFixture(listOf(upgrade))
    val crafter = fixture.givenCrafter(
      items = listOf(
        stack(INPUT_ITEM, 9),
        instance(TARGET_ITEM, uniqueId = 1, upgradeLevel = 3),
        instance(TARGET_ITEM, uniqueId = 2, upgradeLevel = CraftingService.MAX_UPGRADE_LEVEL)
      ),
      knownSkills = mapOf(SKILL_ID to 1)
    )

    fixture.service.resolve(fixture.world, crafter, crafting(upgrade.id, targetUniqueId = 1))
    assertEquals(4, fixture.inventoryOf(crafter).getItems().single { it.uniqueId == 1L }.upgradeLevel)

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, upgrade.id, targetUniqueId = 2)
    )
  }

  /**
   * Cutting a slot is capped by Item Customization rather than by luck, so a crafter at level 3 is refused a
   * second slot outright instead of rolling for one.
   */
  @Test
  fun `a slot cut is refused once the crafters Item Customization cap is reached`() {
    val cut = recipe(id = 8, identifier = "CUT", effect = RecipeEffect.ADD_SLOT, requiredSkillId = CUSTOMIZATION_ID)
    val fixture = CraftingFixture(listOf(cut), skillIds = mapOf("ITEM_CUSTOMIZATION" to CUSTOMIZATION_ID))
    val crafter = fixture.givenCrafter(
      items = listOf(stack(INPUT_ITEM, 9), instance(TARGET_ITEM, uniqueId = 1, slots = 1)),
      knownSkills = mapOf(CUSTOMIZATION_ID to 3)
    )

    assertEquals(
      OpError.CRAFT_NOT_POSSIBLE,
      fixture.service.start(fixture.world, crafter, cut.id, targetUniqueId = 1)
    )
  }

  @Test
  fun `a slot cut at level four is allowed on an item that already has one`() {
    val cut = recipe(id = 9, identifier = "CUT", effect = RecipeEffect.ADD_SLOT, requiredSkillId = CUSTOMIZATION_ID)
    val fixture = CraftingFixture(listOf(cut), skillIds = mapOf("ITEM_CUSTOMIZATION" to CUSTOMIZATION_ID))
    val crafter = fixture.givenCrafter(
      items = listOf(stack(INPUT_ITEM, 9), instance(TARGET_ITEM, uniqueId = 1, slots = 1)),
      knownSkills = mapOf(CUSTOMIZATION_ID to 4)
    )

    assertNull(fixture.service.start(fixture.world, crafter, cut.id, targetUniqueId = 1))

    fixture.service.resolve(fixture.world, crafter, crafting(cut.id, targetUniqueId = 1))
    assertEquals(2, fixture.inventoryOf(crafter).getItems().single { it.uniqueId == 1L }.slots)
  }

  @Test
  fun `a failed slot cut can destroy the item outright`() {
    // Level 1 Item Customization is the docs worst row: a 30% destroy chance, and the recipe never succeeds, so
    // repeating the craft has to lose the item eventually.
    val cut = recipe(
      id = 10, identifier = "CUT", effect = RecipeEffect.ADD_SLOT,
      requiredSkillId = CUSTOMIZATION_ID, baseSuccessChance = 0f
    )
    val fixture = CraftingFixture(listOf(cut), skillIds = mapOf("ITEM_CUSTOMIZATION" to CUSTOMIZATION_ID))

    var destroyed = false
    repeat(60) { attempt ->
      val crafter = fixture.givenCrafter(
        items = listOf(stack(INPUT_ITEM, 9), instance(TARGET_ITEM, uniqueId = 1)),
        knownSkills = mapOf(CUSTOMIZATION_ID to 1)
      )
      fixture.service.resolve(fixture.world, crafter, crafting(cut.id, targetUniqueId = 1))

      if (fixture.inventoryOf(crafter).getItems().none { it.uniqueId == 1L }) {
        destroyed = true
        return@repeat
      }
    }

    assertTrue(destroyed, "60 failed cuts at a 30% destroy chance should have taken the item at least once")
  }

  @Test
  fun `the offered list skips a recipe whose station is missing and keeps one it can afford nothing for`() {
    val atForge = recipe(id = 11, identifier = "AT_FORGE", station = StaticEntityKind.FORGE)
    val anywhere = recipe(id = 12, identifier = "ANYWHERE")

    val fixture = CraftingFixture(listOf(atForge, anywhere))
    val crafter = fixture.givenCrafter(knownSkills = mapOf(SKILL_ID to 1))

    var sent: CraftableRecipesSMSG? = null
    every { fixture.outMessageProcessor.sendToPlayer(any<Long>(), any<CraftableRecipesSMSG>()) } answers {
      sent = secondArg()
    }

    fixture.service.offerRecipes(fixture.world, crafter, SKILL_ID)

    assertNotNull(sent)
    assertEquals(listOf(anywhere.id), sent!!.recipes.map { it.recipeId })
    // Listed with nothing held, which is what tells the player what to go and gather.
    assertFalse(sent!!.recipes.single().inputs.all { it.held >= it.amount })
  }

  /**
   * The whole point of item tiers: a repair a smith can do all day on a plain sword is beyond them on a
   * high-tier one, and Weapon Repair rank 1 reaches tier 20 exactly.
   */
  @Test
  fun `a target above the crafters reach is refused with something they can act on`() {
    val repair = recipe(
      id = 20, identifier = "REPAIR", effect = RecipeEffect.REPAIR, requiredSkillId = REPAIR_ID
    )
    val fixture = CraftingFixture(
      listOf(repair),
      skillIds = mapOf("WEAPON_REPAIR" to REPAIR_ID),
      itemLevels = mapOf(TARGET_ITEM to 21)
    )
    val crafter = fixture.givenCrafter(
      items = listOf(stack(INPUT_ITEM, 9), instance(TARGET_ITEM, uniqueId = 1, durability = 5, maxDurability = 200)),
      knownSkills = mapOf(REPAIR_ID to 1)
    )

    // Not CRAFT_NOT_POSSIBLE: this is the one crafting refusal a player fixes by investing, so it says so.
    assertEquals(
      OpError.CRAFT_ITEM_TOO_ADVANCED,
      fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 1)
    )
  }

  /**
   * An upgrade level counts towards the tier, so improving an item can put it beyond the smith who made it.
   * That interaction is the reason item level and upgrade level are not two unrelated numbers.
   */
  @Test
  fun `upgrades push an item out of reach that its plain twin is inside`() {
    val repair = recipe(
      id = 21, identifier = "REPAIR", effect = RecipeEffect.REPAIR, requiredSkillId = REPAIR_ID
    )
    val fixture = CraftingFixture(
      listOf(repair),
      skillIds = mapOf("WEAPON_REPAIR" to REPAIR_ID),
      itemLevels = mapOf(TARGET_ITEM to 20)
    )
    val crafter = fixture.givenCrafter(
      items = listOf(
        stack(INPUT_ITEM, 9),
        instance(TARGET_ITEM, uniqueId = 1, durability = 5, maxDurability = 200),
        instance(TARGET_ITEM, uniqueId = 2, durability = 5, maxDurability = 200, upgradeLevel = 1)
      ),
      knownSkills = mapOf(REPAIR_ID to 1)
    )

    assertNull(fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 1))
    fixture.world.remove(crafter, Crafting::class)

    assertEquals(
      OpError.CRAFT_ITEM_TOO_ADVANCED,
      fixture.service.start(fixture.world, crafter, repair.id, targetUniqueId = 2)
    )
  }

  @Test
  fun `the offered list hides a recipe whose output is beyond the crafter`() {
    val beyond = recipe(id = 22, identifier = "BEYOND", requiredSkillId = CARPENTRY_ID)
    val fixture = CraftingFixture(
      listOf(beyond),
      skillIds = mapOf("CARPENTRY" to CARPENTRY_ID),
      itemLevels = mapOf(OUTPUT_ITEM to 11)
    )
    val crafter = fixture.givenCrafter(knownSkills = mapOf(CARPENTRY_ID to 1))

    var sent: CraftableRecipesSMSG? = null
    every { fixture.outMessageProcessor.sendToPlayer(any<Long>(), any<CraftableRecipesSMSG>()) } answers {
      sent = secondArg()
    }

    fixture.service.offerRecipes(fixture.world, crafter, CARPENTRY_ID)

    assertNotNull(sent)
    assertTrue(sent!!.recipes.isEmpty(), "Carpentry rank 1 reaches tier 10, and this output is tier 11")
  }

  /** An item the catalogue does not know is out of reach, not trivially the easiest thing there is. */
  @Test
  fun `an output the catalogue has no level for is refused`() {
    val unknown = recipe(id = 23, identifier = "UNKNOWN_OUTPUT")
    val fixture = CraftingFixture(listOf(unknown))
    every { fixture.itemTemplates.levelOf(any()) } returns null

    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))

    assertEquals(
      OpError.CRAFT_ITEM_TOO_ADVANCED,
      fixture.service.start(fixture.world, crafter, unknown.id, targetUniqueId = 0)
    )
  }

  private fun crafting(recipeId: Long, targetUniqueId: Long = 0L) =
    Crafting(recipeId = recipeId, targetUniqueId = targetUniqueId, totalSeconds = 1f)

  private companion object {
    const val CUSTOMIZATION_ID = 11L
    const val CARPENTRY_ID = 9L
    const val REPAIR_ID = 15L
  }
}
