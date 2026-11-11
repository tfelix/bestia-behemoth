package net.bestia.zone.teleport

import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WarpPortalTest {

  private val world = testWorld()
  private val service = SkillTeleportService(mockk(relaxed = true), TeleportItemSettings(), mockk(), mockk(), mockk())
  private val interaction = WarpPortalInteraction(TeleportItemSettings())

  @Test
  fun `a portal is two linked ends that share their passages`() {
    val caster = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(0, 0, 0))) }
    assertNull(service.openPortal(world, caster, Vec3L(0, 0, 0), Vec3L(1000, 0, 0), passages = 1, seconds = 30))

    val ends = ArrayList<Long>()
    world.query(WarpPortalEnd::class).each { ends += it }
    val here = ends.first { world.get(it, Position::class)!!.toVec3L() == Vec3L(0, 0, 0) }

    val walker = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(1, 0, 0))) }
    assertNull(interaction.interact(world, walker, here))
    assertEquals(Teleporting.Destination.Spot(Vec3L(1002, 0, 0)), world.get(walker, Teleporting::class)!!.destination)

    val second = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(1, 0, 0))) }
    assertEquals(OpError.PORTAL_SPENT, interaction.interact(world, second, here))
  }

  @Test
  fun `both ends close when the time is over`() {
    val ticking = testWorld(systems = listOf(WarpPortalSystem()))
    val caster = ticking.createEntity { id -> add(id, Position.fromVec3(Vec3L(0, 0, 0))) }
    service.openPortal(ticking, caster, Vec3L(0, 0, 0), Vec3L(1000, 0, 0), passages = 4, seconds = 2)

    repeat(3) { ticking.tick(1f) }

    var left = 0
    ticking.query(WarpPortalEnd::class).each { left++ }
    assertEquals(0, left)
  }
}
