package net.bestia.zone.crafting

import net.bestia.zone.entity.StaticEntityKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The material chains, checked against the real catalogue: dropped or gathered, refined at a station, then used by a
 * recipe. The docs' crafting chains page is generated from the same files (`./gradlew craftGraph`), so these are
 * what keeps the page and the game saying the same thing.
 */
class CraftingChainTest {

  private val catalog = CraftingCatalog()

  @Test
  fun `every producing recipe can be learned, by being a starter recipe or by being discoverable`() {
    val stranded = catalog.recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .filterNot { catalog.starterKit.isStarterBlueprint(it.id) }
      .filter { catalog.blueprints.pointOf(it.id) == null }
      .map { it.identifier }

    assertTrue(
      stranded.isEmpty(),
      "These recipes are not in starter_kit.yml and have an input without craft-axes, so no master can ever " +
        "learn them: $stranded"
    )
  }

  @Test
  fun `an ore carries no craft axes, so refining it is never something to discover`() {
    val ores = catalog.items.filter { it.identifier.endsWith("_ore") }

    assertTrue(ores.isNotEmpty())
    assertEquals(emptyList<String>(), ores.filter { it.craftAxes.isNotEmpty() }.map { it.identifier })
  }

  @Test
  fun `no material is made out of itself, however many steps it takes`() {
    val madeFrom = catalog.recipes.all()
      .filter { it.effect == RecipeEffect.PRODUCE }
      .groupBy({ catalog.item(it.output!!.itemId).identifier }, { recipe -> recipe.inputs.map { catalog.item(it.itemId).identifier } })
      .mapValues { (_, lists) -> lists.flatten().toSet() }

    val done = mutableSetOf<String>()

    fun visit(item: String, path: List<String>) {
      require(item !in path) { "A recipe chain loops back on itself: ${(path + item).joinToString(" -> ")}" }
      if (!done.add(item)) return
      madeFrom[item]?.forEach { visit(it, path + item) }
    }

    madeFrom.keys.forEach { visit(it, emptyList()) }
  }

  @Test
  fun `every station a recipe is worked at can be built from a kit that a recipe makes`() {
    val made = catalog.recipes.all().mapNotNull { it.output }.map { catalog.item(it.itemId).identifier }.toSet()

    val without = catalog.recipes.stations().filter { "${it.name.lowercase()}_kit" !in made }

    assertEquals(emptyList<StaticEntityKind>(), without)
  }

  @Test
  fun `a station kit is Carpentry, except the workbench which every master can build`() {
    val carpentry = catalog.skillIdByName.getValue("M_CARPENTRY")
    val basic = catalog.skillIdByName.getValue("M_BASIC_SKILL")

    val stations = listOf(
      StaticEntityKind.FURNACE, StaticEntityKind.FORGE, StaticEntityKind.TANNING_RACK, StaticEntityKind.LOOM,
      StaticEntityKind.GLASS_FURNACE, StaticEntityKind.GLASSBLOWING_BENCH
    )
    stations.forEach { station ->
      val kit = kitRecipe(station)
      assertEquals(carpentry, kit.requiredSkillId, "${kit.identifier} should be a Carpentry recipe")
    }

    assertEquals(basic, kitRecipe(StaticEntityKind.WORKBENCH).requiredSkillId)
  }

  @Test
  fun `the tanning rack, the loom and the two glass stations ask for no skill to run`() {
    val basic = catalog.skillIdByName.getValue("M_BASIC_SKILL")

    val tooDemanding = catalog.recipes.all()
      .filter { it.station in NO_SKILL_STATIONS }
      .filter { it.requiredSkillId != basic }
      .map { it.identifier }

    assertTrue(catalog.recipes.all().any { it.station in NO_SKILL_STATIONS })
    assertEquals(emptyList<String>(), tooDemanding)
  }

  private fun kitRecipe(station: StaticEntityKind): Recipe {
    val kitItem = catalog.item("${station.name.lowercase()}_kit").id

    return catalog.recipes.all().single { it.output?.itemId == kitItem }
  }

  private companion object {
    /** Stations where nobody needs a skill: the rack, the loom and the two glass stations. */
    val NO_SKILL_STATIONS = setOf(
      StaticEntityKind.TANNING_RACK, StaticEntityKind.LOOM,
      StaticEntityKind.GLASS_FURNACE, StaticEntityKind.GLASSBLOWING_BENCH
    )
  }
}
