package net.bestia.zone.crafting

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * A crafter, a world, and a [CraftingService] whose only mocks are the things that leave the tick: the durable
 * inventory, the async executor, the station lookup and the socket.
 *
 * The registries and [MasterCraftBonusService] are real, so a test exercises the actual per-level tables and the
 * actual recipe invariants rather than a restatement of them.
 */
class CraftingFixture(
  recipeList: List<Recipe> = emptyList(),

  /** Identifier -> id, for the skills [MasterCraftBonusService] resolves lazily. */
  skillIds: Map<String, Long> = emptyMap(),

  /**
   * Item id -> tier, for the item-level ceilings.
   *
   * Empty means every item is tier 1, which is what most tests want: an unspecified item should not accidentally
   * be beyond a crafter and turn a test about materials into a test about reach.
   */
  itemLevels: Map<Long, Int> = emptyMap()
) {

  val world: World = testWorld()

  val recipes = RecipeRegistry().apply { load(recipeList) }

  val skillRepository = mockk<SkillRepository>()
  val inventoryService = mockk<InventoryService>(relaxed = true)
  val structures = mockk<PlayerStructureService>()
  val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  val masterRepository = mockk<MasterRepository>(relaxed = true)

  val itemTemplates = mockk<ItemTemplateRegistry>().also { templates ->
    every { templates.levelOf(any()) } answers { itemLevels[firstArg<Long>()] ?: DEFAULT_ITEM_LEVEL }
  }

  /** Runs submitted jobs immediately, so a test can assert the durable side without waiting on a thread. */
  val asyncJobExecutor = mockk<AsyncJobExecutor>().also { executor ->
    every { executor.submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
    every { executor.submit(any()) } answers { firstArg<() -> Unit>().invoke() }
  }

  val bonuses: MasterCraftBonusService

  val service: CraftingService

  /** Stations the fixture pretends are standing next to the crafter. */
  private val stationsInRange = mutableSetOf<StaticEntityKind>()

  init {
    every { skillRepository.findByIdentifier(any()) } answers {
      skillIds[firstArg<String>()]?.let { id -> mockk<Skill>().also { every { it.id } returns id } }
    }
    every { structures.stationNear(any(), any(), any()) } answers {
      if (thirdArg<StaticEntityKind>() in stationsInRange) STATION_ENTITY_ID else null
    }

    bonuses = MasterCraftBonusService(skillRepository)
    service = CraftingService(
      recipes = recipes,
      bonuses = bonuses,
      inventoryService = inventoryService,
      structures = structures,
      outMessageProcessor = outMessageProcessor,
      asyncJobExecutor = asyncJobExecutor,
      itemTemplates = itemTemplates,
      masterRepository = masterRepository
    )
  }

  fun withStation(kind: StaticEntityKind): CraftingFixture {
    stationsInRange.add(kind)
    return this
  }

  /**
   * A master entity holding [items], knowing [knownSkills], standing at the origin.
   *
   * [Master] is what makes it a legal crafter at all - `CraftingService` refuses a bestia, since the durable
   * side of a craft goes through the master container.
   */
  fun givenCrafter(
    items: List<Inventory.Item> = emptyList(),
    knownSkills: Map<Long, Int> = emptyMap(),
    masterId: Long = MASTER_ID
  ): EntityId = world.createEntity { id ->
    add(id, Master(masterId = masterId, name = "Smith"))
    add(id, Account(accountId = ACCOUNT_ID))
    add(id, Position(0, 0, 0))
    add(id, Inventory(items.toMutableList()))
    add(id, KnownSkills(knownSkills.toMutableMap()))
  }

  fun inventoryOf(entityId: EntityId): Inventory = world.getOrThrow(entityId, Inventory::class)

  companion object {
    const val MASTER_ID = 42L
    const val ACCOUNT_ID = 7L
    const val STATION_ENTITY_ID = 999L

    /** A plain material pile, which is the only thing a recipe input may be. */
    fun stack(itemId: Long, amount: Int) = Inventory.Item(itemId = itemId, amount = amount, weight = 1)

    /** A unique instance, which is the only thing a targeted recipe may work on. */
    fun instance(
      itemId: Long,
      uniqueId: Long,
      durability: Int = 0,
      maxDurability: Int = 0,
      slots: Int = 0,
      upgradeLevel: Int = 0,
      equipped: Boolean = false
    ) = Inventory.Item(
      itemId = itemId,
      amount = 1,
      weight = 1,
      uniqueId = uniqueId,
      stackable = false,
      equipped = equipped,
      durability = durability,
      maxDurability = maxDurability,
      slots = slots,
      upgradeLevel = upgradeLevel
    )

    fun recipe(
      id: Long,
      identifier: String,
      effect: RecipeEffect = RecipeEffect.PRODUCE,
      output: Recipe.ItemStack? = Recipe.ItemStack(itemId = OUTPUT_ITEM, amount = 1),
      inputs: List<Recipe.ItemStack> = listOf(Recipe.ItemStack(itemId = INPUT_ITEM, amount = 2)),
      requiredSkillId: Long = SKILL_ID,
      requiredSkillLevel: Int = 1,
      station: StaticEntityKind? = null,
      craftSeconds: Float = 4f,
      baseSuccessChance: Float = 1f
    ) = Recipe(
      id = id,
      identifier = identifier,
      effect = effect,
      output = if (effect == RecipeEffect.PRODUCE) output else null,
      inputs = inputs,
      requiredSkillId = requiredSkillId,
      requiredSkillLevel = requiredSkillLevel,
      station = station,
      craftSeconds = craftSeconds,
      baseSuccessChance = baseSuccessChance
    )

    /** What an item the fixture was told nothing about counts as - the easiest tier there is. */
    const val DEFAULT_ITEM_LEVEL = 1

    const val SKILL_ID = 100L
    const val INPUT_ITEM = 8L
    const val OUTPUT_ITEM = 9L
    const val TARGET_ITEM = 19L
  }
}
