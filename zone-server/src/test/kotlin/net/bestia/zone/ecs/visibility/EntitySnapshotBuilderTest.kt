package net.bestia.zone.ecs.visibility

import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualComponentSMSG
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.InventoryComponentSMSG
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.PathSMSG
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntitySnapshotBuilderTest {

  private val builder = EntitySnapshotBuilder()
  private val observer = 42L

  @Test
  fun `a visual comes before the position, which comes before the path`() {
    val world = testWorld()
    val entity = world.createEntity { id ->
      add(id, Path(mutableListOf(Vec3L(2, 0, 0))))
      add(id, Position(1, 0, 0))
      add(id, Speed(4f))
      add(id, EntityVisual(VisualKind.BESTIA, 7L))
    }

    val order = world.read { builder.build(this, entity, observer) }.map { it::class }

    assertTrue(
      order.indexOf(VisualComponentSMSG::class) < order.indexOf(PositionSMSG::class),
      "a node with no visual child drops everything routed through it, once per frame while walking"
    )
    assertTrue(
      order.indexOf(PositionSMSG::class) < order.indexOf(PathSMSG::class),
      "update_path anchors on the current position, which is the world origin on a node made this frame"
    )
  }

  @Test
  fun `an owner-only component is left out even for its own owner`() {
    val world = testWorld()
    val entity = world.createEntity { id ->
      add(id, Position(1, 0, 0))
      add(id, Account(observer))
      add(id, Inventory(mutableListOf()))
    }

    val snapshot = world.read { builder.build(this, entity, observer) }

    assertTrue(
      snapshot.none { it is InventoryComponentSMSG },
      "coming into view is not the event that re-sends an inventory - GetSelfHandler owns that channel"
    )
  }

  @Test
  fun `a public component reaches an unrelated observer`() {
    val world = testWorld()
    val entity = world.createEntity { id ->
      add(id, Position(1, 0, 0))
      add(id, EntityVisual(VisualKind.ITEM, 3L))
    }

    val snapshot = world.read { builder.build(this, entity, 99L) }

    assertTrue(snapshot.any { it is VisualComponentSMSG })
    assertTrue(snapshot.any { it is PositionSMSG })
  }

  @Test
  fun `every message names the entity it describes`() {
    val world = testWorld()
    val entity = world.createEntity { id ->
      add(id, Position(1, 0, 0))
      add(id, EntityVisual(VisualKind.BESTIA, 1L))
      add(id, Health(current = 5, max = 10))
    }

    val snapshot = world.read { builder.build(this, entity, observer) }

    assertEquals(3, snapshot.size, "visual, position and health - and nothing invented")
    assertTrue(snapshot.all { it.entityId == entity })
  }
}
