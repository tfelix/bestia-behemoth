package net.bestia.zone.crafting

import io.mockk.every
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.crafting.CraftingFixture.Companion.ACCOUNT_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.INPUT_ITEM
import net.bestia.zone.crafting.CraftingFixture.Companion.MASTER_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.SKILL_ID
import net.bestia.zone.crafting.CraftingFixture.Companion.recipe
import net.bestia.zone.crafting.CraftingFixture.Companion.stack
import net.bestia.zone.item.ecs.ObtainItemIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** What a craft pays in EXP: the time it took, on success only, to the master whoever did the work. */
class WorkExpCraftingTest {

  /** Takes 1920 s, which is what an item of level 40 takes to forge. */
  private val longCraft = recipe(id = 1, identifier = "LONG", craftSeconds = 1920f)

  private fun CraftingFixture.finishWith(crafter: Long, recipe: Recipe) {
    assertNull(service.start(world, crafter, recipe.id, targetUniqueId = 0))
    finishCraftOf(crafter)
  }

  @Test
  fun `a successful craft pays the master for the time it took`() {
    val fixture = CraftingFixture(listOf(longCraft))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))
    fixture.world.add(crafter, Level(40))

    fixture.finishWith(crafter, longCraft)

    val exp = fixture.world.get(crafter, GainExp::class)?.value
    assertNotNull(exp)
    assertTrue(abs(exp!! - 9504) <= 1) { "paid $exp" }
  }

  @Test
  fun `a craft that fails pays nothing`() {
    val doomed = recipe(id = 2, identifier = "DOOMED", craftSeconds = 1920f, baseSuccessChance = 0f)
    val fixture = CraftingFixture(listOf(doomed))
    val crafter = fixture.givenCrafter(items = listOf(stack(INPUT_ITEM, 5)), knownSkills = mapOf(SKILL_ID to 1))
    fixture.world.add(crafter, Level(40))

    fixture.finishWith(crafter, doomed)

    // A craft at the minimum chance can still land once in a hundred; what it never does is pay without making it.
    val made = fixture.world.get(crafter, ObtainItemIntent.CreateItemIntent::class) != null
    assertEquals(made, fixture.world.get(crafter, GainExp::class) != null)
  }

  @Test
  fun `a bestia's craft pays its master who plays`() {
    val fixture = CraftingFixture(listOf(longCraft))
    val master = fixture.givenCrafter(knownSkills = mapOf(SKILL_ID to 1))
    fixture.world.add(master, Level(40))
    val bestia = fixture.givenBestia(items = listOf(stack(INPUT_ITEM, 5)))
    every { fixture.sessions.isPlaying(ACCOUNT_ID, MASTER_ID) } returns true
    every { fixture.sessions.getSelectedMasterEntityId(ACCOUNT_ID) } returns master

    assertNull(fixture.service.start(fixture.world, bestia, longCraft.id, targetUniqueId = 0, masterEntityId = master))
    fixture.service.resolve(fixture.world, bestia, fixture.world.get(bestia, Crafting::class)!!)

    assertNotNull(fixture.world.get(master, GainExp::class))
    assertNull(fixture.world.get(bestia, GainExp::class))
  }

  @Test
  fun `a bestia working while its master is away pays nobody`() {
    val fixture = CraftingFixture(listOf(longCraft))
    val master = fixture.givenCrafter(knownSkills = mapOf(SKILL_ID to 1))
    fixture.world.add(master, Level(40))
    val bestia = fixture.givenBestia(items = listOf(stack(INPUT_ITEM, 5)))

    assertNull(fixture.service.start(fixture.world, bestia, longCraft.id, targetUniqueId = 0, masterEntityId = master))
    fixture.service.resolve(fixture.world, bestia, fixture.world.get(bestia, Crafting::class)!!)

    assertNull(fixture.world.get(master, GainExp::class))
    assertNull(fixture.world.get(bestia, GainExp::class))
  }
}
