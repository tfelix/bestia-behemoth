package net.bestia.zone.crafting

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.world.prop.StaticEntityKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The design docs' per-level tables, asserted at their documented endpoints rather than every row.
 *
 * Endpoints are where a table is actually wrong when it is wrong: an off-by-one in the indexing shows at level 1,
 * and a table one row too short shows at the top. The middle rows are arithmetic between the two.
 */
class MasterCraftBonusServiceTest {

  private val skills = mockk<SkillRepository>()
  private val service: MasterCraftBonusService

  init {
    every { skills.findByIdentifier(any()) } answers {
      SKILL_IDS[firstArg<String>()]?.let { id -> mockk<Skill>().also { every { it.id } returns id } }
    }
    service = MasterCraftBonusService(skills)
  }

  @Test
  fun `carpentry is worth five percent a level up to fifty`() {
    val recipe = recipeRequiring(CARPENTRY)

    assertEquals(0.05f, service.successChance(known(CARPENTRY to 1), recipe), TOLERANCE)
    assertEquals(0.50f, service.successChance(known(CARPENTRY to 10), recipe), TOLERANCE)
  }

  @Test
  fun `master craftsman adds two percent a level on top of carpentry`() {
    val recipe = recipeRequiring(CARPENTRY)

    assertEquals(0.05f + 0.02f, service.successChance(known(CARPENTRY to 1, TINKERER to 1), recipe), TOLERANCE)
    assertEquals(0.50f + 0.10f, service.successChance(known(CARPENTRY to 10, TINKERER to 5), recipe), TOLERANCE)
  }

  @Test
  fun `ore refinement is thirty percent a level over three levels`() {
    val recipe = recipeRequiring(ORE_REFINEMENT)

    assertEquals(0.30f, service.successChance(known(ORE_REFINEMENT to 1), recipe), TOLERANCE)
    assertEquals(0.90f, service.successChance(known(ORE_REFINEMENT to 3), recipe), TOLERANCE)
  }

  /** Both smithing passives feed forging, plus the forge skill's own step. */
  @Test
  fun `forging adds weaponry research and master smith`() {
    val recipe = recipeRequiring(FORGE_WEAPON)

    val maxed = known(FORGE_WEAPON to 10, WEAPONRY_RESEARCH to 10, MASTER_SMITH to 5)
    assertEquals((0.10f + 0.25f + 0.20f).coerceAtMost(1f), service.successChance(maxed, recipe), TOLERANCE)
  }

  @Test
  fun `upgrading adds four percent a level of research and five of master smith`() {
    val recipe = recipeRequiring(UPGRADE_EQUIPMENT, baseSuccessChance = 0f)

    assertEquals(0.04f, service.successChance(known(WEAPONRY_RESEARCH to 1), recipe), TOLERANCE)
    assertEquals(0.40f + 0.25f, service.successChance(known(WEAPONRY_RESEARCH to 10, MASTER_SMITH to 5), recipe), TOLERANCE)
  }

  /** A repair has no documented success table, so nothing may be invented for it. */
  @Test
  fun `weapon repair gets no bonus at all`() {
    val recipe = recipeRequiring(WEAPON_REPAIR, baseSuccessChance = 0.8f)

    assertEquals(0.8f, service.successChance(known(WEAPON_REPAIR to 5), recipe), TOLERANCE)
  }

  @Test
  fun `a chance never leaves zero to one`() {
    val certain = recipeRequiring(CARPENTRY, baseSuccessChance = 1f)

    assertEquals(1f, service.successChance(known(CARPENTRY to 10, TINKERER to 5), certain), TOLERANCE)
  }

  @Test
  fun `master craftsman shortens a craft by ten percent a level`() {
    val recipe = recipeRequiring(CARPENTRY, craftSeconds = 10f)

    assertEquals(9f, service.craftSeconds(known(TINKERER to 1), recipe), TOLERANCE)
    assertEquals(5f, service.craftSeconds(known(TINKERER to 5), recipe), TOLERANCE)
  }

  /**
   * Cooking has its own reduction and must not also collect Master Craftsman's - the two sit in different trees,
   * so a crafter holding both would otherwise get a discount the design never granted.
   */
  @Test
  fun `cooking uses its own reduction and does not stack with master craftsman`() {
    val recipe = recipeRequiring(COOKING, craftSeconds = 10f)

    assertEquals(10f, service.craftSeconds(known(COOKING to 1, TINKERER to 5), recipe), TOLERANCE)
    assertEquals(6f, service.craftSeconds(known(COOKING to 3, TINKERER to 5), recipe), TOLERANCE)
  }

  @Test
  fun `a craft never resolves in the tick it started`() {
    val instant = recipeRequiring(CARPENTRY, craftSeconds = MasterCraftBonusService.MIN_CRAFT_SECONDS / 2f)

    assertEquals(
      MasterCraftBonusService.MIN_CRAFT_SECONDS,
      service.craftSeconds(known(TINKERER to 5), instant),
      TOLERANCE
    )
  }

  @Test
  fun `item customization allows one slot to level three and three from level four`() {
    assertEquals(0, service.maxSlots(known()))
    assertEquals(1, service.maxSlots(known(ITEM_CUSTOMIZATION to 1)))
    assertEquals(1, service.maxSlots(known(ITEM_CUSTOMIZATION to 3)))
    assertEquals(3, service.maxSlots(known(ITEM_CUSTOMIZATION to 4)))
    assertEquals(3, service.maxSlots(known(ITEM_CUSTOMIZATION to 10)))
  }

  @Test
  fun `the destroy chance falls from thirty percent to twelve`() {
    assertEquals(0.30f, service.destroyChance(known(ITEM_CUSTOMIZATION to 1)), TOLERANCE)
    assertEquals(0.12f, service.destroyChance(known(ITEM_CUSTOMIZATION to 10)), TOLERANCE)
  }

  /** Reaching a slot cut without the skill means a bug let it through, and a bug is not "no risk". */
  @Test
  fun `a crafter with no item customization gets the worst destroy chance rather than none`() {
    assertEquals(0.30f, service.destroyChance(known()), TOLERANCE)
    assertEquals(0.30f, service.destroyChance(null), TOLERANCE)
  }

  @Test
  fun `carpentry reaches item tier ten a level and loses the ceiling at full rank`() {
    val recipe = recipeRequiring(CARPENTRY)

    assertEquals(0, service.maxItemLevel(known(), recipe), "no Carpentry reaches nothing at all")
    assertEquals(10, service.maxItemLevel(known(CARPENTRY to 1), recipe))
    assertEquals(90, service.maxItemLevel(known(CARPENTRY to 9), recipe))
    // The docs read "100+", and the plus is taken literally: at full rank the master stops being the limit.
    assertEquals(
      MasterCraftBonusService.NO_ITEM_LEVEL_CAP,
      service.maxItemLevel(known(CARPENTRY to 10), recipe)
    )
  }

  @Test
  fun `weapon repair reaches item tier twenty a level and loses the ceiling at full rank`() {
    val recipe = recipeRequiring(WEAPON_REPAIR)

    assertEquals(20, service.maxItemLevel(known(WEAPON_REPAIR to 1), recipe))
    assertEquals(80, service.maxItemLevel(known(WEAPON_REPAIR to 4), recipe))
    assertEquals(
      MasterCraftBonusService.NO_ITEM_LEVEL_CAP,
      service.maxItemLevel(known(WEAPON_REPAIR to 5), recipe)
    )
  }

  /** Seven of the nine skills have no documented ceiling, and inventing one would be inventing content. */
  @Test
  fun `a skill the docs give no ceiling has none`() {
    for (identifier in listOf(COOKING, ITEM_CUSTOMIZATION, ORE_REFINEMENT, FORGE_WEAPON, UPGRADE_EQUIPMENT)) {
      assertEquals(
        MasterCraftBonusService.NO_ITEM_LEVEL_CAP,
        service.maxItemLevel(known(identifier to 1), recipeRequiring(identifier)),
        "$identifier should have no item level ceiling"
      )
    }
  }

  /**
   * A level past the end of a table means the catalogue raised a maxLevel, which should degrade to the best
   * documented row rather than throw and kill a craft in progress.
   */
  @Test
  fun `a level past the end of a table clamps to its last row`() {
    val recipe = recipeRequiring(ORE_REFINEMENT)

    assertEquals(0.90f, service.successChance(known(ORE_REFINEMENT to 99), recipe), TOLERANCE)
  }

  private fun known(vararg levels: Pair<String, Int>) =
    KnownSkills(levels.associate { (identifier, level) -> SKILL_IDS.getValue(identifier) to level }.toMutableMap())

  private fun recipeRequiring(
    identifier: String,
    baseSuccessChance: Float = 0f,
    craftSeconds: Float = 4f
  ) = Recipe(
    id = 1,
    identifier = "TEST",
    effect = RecipeEffect.PRODUCE,
    output = Recipe.ItemStack(itemId = 1, amount = 1),
    inputs = listOf(Recipe.ItemStack(itemId = 2, amount = 1)),
    requiredSkillId = SKILL_IDS.getValue(identifier),
    requiredSkillLevel = 1,
    station = StaticEntityKind.WORKBENCH,
    craftSeconds = craftSeconds,
    baseSuccessChance = baseSuccessChance
  )

  private companion object {
    const val TOLERANCE = 0.0001f

    const val CARPENTRY = "CARPENTRY"
    const val TINKERER = "TINKERER"
    const val ITEM_CUSTOMIZATION = "ITEM_CUSTOMIZATION"
    const val ORE_REFINEMENT = "ORE_REFINEMENT"
    const val WEAPONRY_RESEARCH = "WEAPONRY_RESEARCH"
    const val MASTER_SMITH = "MASTER_SMITH"
    const val COOKING = "COOKING"
    const val FORGE_WEAPON = "FORGE_WEAPON"
    const val WEAPON_REPAIR = "WEAPON_REPAIR"
    const val UPGRADE_EQUIPMENT = "UPGRADE_EQUIPMENT"

    /** Arbitrary but distinct, since the service only ever compares them. */
    val SKILL_IDS = mapOf(
      CARPENTRY to 9L,
      TINKERER to 10L,
      ITEM_CUSTOMIZATION to 11L,
      ORE_REFINEMENT to 12L,
      FORGE_WEAPON to 13L,
      WEAPON_REPAIR to 15L,
      WEAPONRY_RESEARCH to 16L,
      MASTER_SMITH to 17L,
      COOKING to 3L,
      UPGRADE_EQUIPMENT to 44L
    )
  }
}
