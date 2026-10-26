package net.bestia.zone.crafting.discovery

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.KnownRecipeStore
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.StarterKit
import net.bestia.zone.crafting.ecs.KnownRecipes
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DiscoveryServiceTest {

  private val axes = MaterialAxes().apply {
    load(
      mapOf(
        HERB to CraftVector(mapOf(CraftAxis.STATE to -1.0, CraftAxis.VITALITY to 0.9)),
        WATER to CraftVector(mapOf(CraftAxis.STATE to -1.0)),
        STONE to CraftVector(mapOf(CraftAxis.STATE to 1.0, CraftAxis.HEFT to 0.6)),
      )
    )
  }

  /** One herb to two water: STATE -1, VITALITY 0.3. */
  private val potion = Recipe(
    id = 1, identifier = "POTION", effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(itemId = POTION_ITEM, amount = 1),
    inputs = listOf(Recipe.ItemStack(HERB, 1), Recipe.ItemStack(WATER, 2)),
    requiredSkillId = SKILL, requiredSkillLevel = 1, station = null, craftSeconds = 1f, baseSuccessChance = 1f,
  )

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>(relaxed = true).also {
    every { it.takeNamedPiles(any(), any(), any()) } returns true
  }
  private val knownRecipeStore = mockk<KnownRecipeStore>(relaxed = true)
  private val sent = mutableListOf<SMSG>()
  private val outMessageProcessor = mockk<OutMessageProcessor>().also { messages ->
    val captured = slot<SMSG>()
    every { messages.sendToPlayer(any(), capture(captured)) } answers { sent += captured.captured }
  }
  private val inlineJobs = mockk<AsyncJobExecutor>().also { executor ->
    every { executor.submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private fun service(roll: Float, itemLevel: Int = 1): DiscoveryService {
    val starterKit = StarterKit()
    return DiscoveryService(
      crafters = Crafters(starterKit),
      blueprints = BlueprintPoints().apply { load(listOf(potion), axes, starterKit) },
      materialAxes = axes,
      itemTemplates = mockk<ItemTemplateRegistry> { every { baseLevelOf(any()) } returns itemLevel },
      structures = mockk(relaxed = true),
      inventoryService = inventoryService,
      knownRecipeStore = knownRecipeStore,
      asyncJobExecutor = inlineJobs,
      outMessageProcessor = outMessageProcessor,
      random = FixedRandom(roll),
    )
  }

  private val herbs = Inventory.Item(HERB, amount = 5)
  private val water = Inventory.Item(WATER, amount = 5)
  private val stones = Inventory.Item(STONE, amount = 5)

  private val master: EntityId = world.createEntity { id ->
    add(id, Master(masterId = MASTER_ID, name = "Alice"))
    add(id, Account(accountId = ACCOUNT_ID))
    add(id, Position(0, 0, 0))
    add(id, Inventory(mutableListOf(herbs, water, stones)))
    add(id, KnownSkills(mutableMapOf(SKILL to 1)))
    add(id, KnownRecipes())
  }

  @Test
  fun `the recipe's own proportions teach the blueprint`() {
    service(roll = 0f).discover(world, master, master, SKILL, listOf(herbs.playerItemId to 1, water.playerItemId to 2))

    assertEquals(DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.LEARNED, recipeId = potion.id), sent.single())
    assertTrue(world.get(master, KnownRecipes::class)!!.knows(potion.id))
    verify { knownRecipeStore.learn(MASTER_ID, potion.id) }
  }

  @Test
  fun `a failed roll in reach slips, and the materials are spent anyway`() {
    service(roll = 0.99f, itemLevel = 100).discover(world, master, master, SKILL, listOf(herbs.playerItemId to 1, water.playerItemId to 2))

    assertEquals(DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.SLIPPED), sent.single())
    assertEquals(4, herbs.amount)
    assertEquals(3, water.amount)
    verify { inventoryService.takeNamedPiles(MASTER_ID, null, any()) }
  }

  /** Herb alone sits at VITALITY 0.9 against the potion's 0.3: off by 0.6, past the level 1 radius of 0.18. */
  @Test
  fun `a mix far off names the axes to move along`() {
    service(roll = 0f).discover(world, master, master, SKILL, listOf(stones.playerItemId to 1))

    val result = sent.single() as DiscoveryResultSMSG
    assertEquals(DiscoveryResultSMSG.Outcome.NOTHING, result.outcome)
    assertEquals(
      listOf(DiscoveryResultSMSG.AxisHint(CraftAxis.STATE, tooHigh = true), DiscoveryResultSMSG.AxisHint(CraftAxis.HEFT, tooHigh = true)),
      result.hints,
      "a stone is far too solid and too heavy for a potion"
    )
  }

  @Test
  fun `a mix just past the skill's reach says so`() {
    val radius = DiscoveryService.radiusOf(1)
    val justBeyond = CraftVector(mapOf(CraftAxis.STATE to -1.0, CraftAxis.VITALITY to 0.3 + radius * 1.2))

    val result = service(roll = 0f).judge(justBeyond, BlueprintPoints().apply { load(listOf(potion), axes, StarterKit()) }.discoverable(SKILL), 1)

    assertEquals(DiscoveryResultSMSG.Outcome.BEYOND_SKILL, result.outcome)
  }

  @Test
  fun `with nothing left to find there is no hint`() {
    world.get(master, KnownRecipes::class)!!.learn(potion.id)

    service(roll = 0f).discover(world, master, master, SKILL, listOf(stones.playerItemId to 1))

    assertEquals(DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.NOTHING), sent.single())
  }

  @Test
  fun `the learn chance falls with the item level and never reaches zero`() {
    assertEquals(1f, DiscoveryService.learnChance(skillLevel = 3, itemLevel = 15))
    assertEquals(0.25f, DiscoveryService.learnChance(skillLevel = 1, itemLevel = 20), 1e-6f)
    assertEquals(0.001f, DiscoveryService.learnChance(skillLevel = 1, itemLevel = 1000))
  }

  private class FixedRandom(private val value: Float) : Random() {
    override fun nextBits(bitCount: Int): Int = 0
    override fun nextFloat(): Float = value
  }

  private companion object {
    const val SKILL = 7L
    const val MASTER_ID = 3L
    const val ACCOUNT_ID = 4L
    const val HERB = 11L
    const val WATER = 12L
    const val STONE = 13L
    const val POTION_ITEM = 20L
  }
}
