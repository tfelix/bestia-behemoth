package net.bestia.zone.companion.query

import io.mockk.every
import io.mockk.mockk
import net.bestia.bus.contract.ZoneAnswer.Roster.Activity
import net.bestia.zone.ai.ecs.AiPipelineFixture
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.RecipeEffect
import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.crafting.station.StationPause
import net.bestia.zone.crafting.station.StationWork
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.QueueEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BestiaActivitiesTest {

  private lateinit var ai: AiPipelineFixture
  private val work = StationWork()
  private lateinit var activities: BestiaActivities

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    val recipe = Recipe(
      id = RECIPE, identifier = "PLANK", effect = RecipeEffect.PRODUCE, output = Recipe.ItemStack(PLANK, 1),
      inputs = emptyList(), requiredSkillId = 10L, requiredSkillLevel = 1, station = StaticEntityKind.WORKBENCH,
      craftSeconds = 1f, baseSuccessChance = 1f,
    )
    val items = mockk<ItemTemplateRegistry> { every { identifierOf(PLANK) } returns "plank" }
    activities = BestiaActivities(ai.structureRegistry, RecipeRegistry().apply { load(listOf(recipe)) }, items, work)
  }

  private fun owned(job: BestiaJob, profile: String = "passive_wanderer"): EntityId {
    val id = ai.spawnMob(profile, Vec3L(0, 0, 0), orders = AiConfig(job))
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    return id
  }

  @Test
  fun `a crafter at work tells what it makes, how far it is and why it waits`() {
    val crafter = owned(BestiaJob.CRAFT)
    ai.structures.add(
      StructureEntry(
        5L, StaticEntityKind.WORKBENCH, MASTER, ownerAccountId = null, Vec3L(4, 0, 0), yaw = 0f,
        queue = listOf(QueueEntry(RECIPE, amount = 20, done = 4)), takesAnyCrafter = true,
      )
    )
    ai.tickUntil { ai.world.get(crafter, WorkAssignment::class) != null }
    work.of(5L).pause = StationPause.BAG_FULL

    assertEquals(
      Activity("CRAFTING", item = "plank", done = 4, total = 20, station = "WORKBENCH", pause = "BAG_FULL"),
      activities.of(ai.world, crafter, BestiaJob.CRAFT),
    )
  }

  @Test
  fun `a guard with nothing to do is guarding, anyone else idle`() {
    assertEquals("GUARDING", activities.of(ai.world, owned(BestiaJob.GUARD), BestiaJob.GUARD)?.kind)
    assertEquals("IDLE", activities.of(ai.world, owned(BestiaJob.REST), BestiaJob.REST)?.kind)
  }

  private companion object {
    const val MASTER = 7L
    const val RECIPE = 1L
    const val PLANK = 4711L
  }
}
