package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.stronghold.EmpyriteSeeds
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.EmpyriteSeedSprout
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmpyriteSeedSproutsTest {

  private val generated = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  private val worldService = mockk<WorldService>().also { every { it.generated } returns generated }
  private val sprouts = EmpyriteSeedSprouts(worldService)
  private val world = testWorld()

  @Test
  fun `a seed's sprout stands on its spot as a golden structure, once`() {
    val seed = EmpyriteSeeds.of(generated).first()

    sprouts.raise(world, seed)
    sprouts.raise(world, seed)

    val sprout = assertNotNull(sprouts.sproutOf(seed.rank))
    assertEquals(EmpyriteSeedSprout(seed.rank), world.get(sprout, EmpyriteSeedSprout::class))
    val look = EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.EMPYRITE_SEED.ordinal.toLong())
    assertEquals(look, world.get(sprout, EntityVisual::class))
    val at = assertNotNull(world.get(sprout, Position::class))
    assertEquals((seed.x / generated.config.voxelSize).toLong(), at.x)
    var sproutCount = 0
    world.query(EmpyriteSeedSprout::class).each { sproutCount++ }
    assertEquals(1, sproutCount)
  }
}
