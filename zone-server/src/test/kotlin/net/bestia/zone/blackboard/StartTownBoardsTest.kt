package net.bestia.zone.blackboard

import net.bestia.zone.blackboard.ecs.Blackboard
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.settlement.StartTownMarkets
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class StartTownBoardsTest {

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var markets: StartTownMarkets

  @Autowired
  private lateinit var chunks: ChunkService

  @Test
  fun `every start town's market hall has a board on the ground beside its door`() {
    val boards = world.read {
      query(Blackboard::class, Position::class)
        .map { get<Blackboard>() to get<Position>().toVec3L() }
        .filter { (board, _) -> board.structureId == null }
        .map { (_, at) -> at }
    }
    val halls = markets.halls()

    assertTrue(halls.isNotEmpty(), "the test world has start towns with halls")
    assertEquals(halls.size, boards.size)
    for (hall in halls) {
      val metres = chunks.config.voxelSize
      val nearest = boards.minOf { hypot(it.x * metres - hall.building.door.x, it.y * metres - hall.building.door.y) }
      assertTrue(nearest < 5.0, "a board stands within a few steps of the ${hall.townName} hall's door")
    }
    for (board in boards) {
      val ground = chunks.surfaceElevationAt(board.x, board.y)!!
      assertTrue(abs(board.z * chunks.config.voxelSize - ground) <= 1.0, "the board at $board stands on the ground")
    }
  }
}
