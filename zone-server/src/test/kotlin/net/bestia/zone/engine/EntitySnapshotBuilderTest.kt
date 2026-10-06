package net.bestia.zone.engine

import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualComponentSMSG
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.InventoryComponentSMSG
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.PathSMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.PositionSMSG
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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

  /** A crowd arriving in one chunk is seen by everyone holding it; the messages are built for the first. */
  @Test
  fun `one snapshot serves every viewer`() {
    val world = testWorld()
    val entity = world.createEntity { id ->
      add(id, Position(1, 0, 0))
      add(id, EntityVisual(VisualKind.BESTIA, 1L))
    }

    val snapshot = world.read { builder.snapshotOf(this, entity) }

    assertEquals(world.read { builder.build(this, entity, observer) }.map { it::class }, snapshot.visibleTo(observer).map { it::class })
    assertSame(snapshot.visibleTo(observer).first(), snapshot.visibleTo(99L).first())
  }
}
