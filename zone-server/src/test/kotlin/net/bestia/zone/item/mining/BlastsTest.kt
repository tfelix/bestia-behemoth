package net.bestia.zone.item.mining

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkStreamInbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlastsTest {

  private val carves = mutableListOf<ChunkStreamInbox.Carve>()
  private val inbox = mockk<ChunkStreamInbox> {
    every { offerCarve(capture(carves)) } returns Unit
  }

  private var tooHard = false
  private val oreInBrush = mockk<OreInBrush> {
    every { scan(any()) } returns emptyList()
    every { anyAbove(any(), any()) } answers { tooHard }
  }

  private val miningSkill = mockk<MiningSkill> { every { levelOf(any()) } returns 1 }

  private val sut = Blasts(oreInBrush, miningSkill, inbox)
  private val world = testWorld()
  private val caster = world.createEntity { }

  @Test
  fun `the rock breaks once the fuse has burnt down, and the ore scatters`() {
    sut.set(ACCOUNT, caster, Vec3L(10, 10, 50), radiusMetres = 3)

    sut.burn(2.9f)
    val early = carves.toList()
    sut.burn(0.2f)

    assertEquals(emptyList(), early)
    val blast = carves.single()
    assertTrue(blast.scatters)
    assertEquals(caster, blast.yieldTo)
    assertEquals(3.0, blast.brush.radius)
  }

  @Test
  fun `a fuse goes off only once`() {
    sut.set(ACCOUNT, caster, Vec3L(10, 10, 50), radiusMetres = 2)

    sut.burn(5f)
    sut.burn(5f)

    assertEquals(1, carves.size)
  }

  @Test
  fun `ore above the caster's Mining refuses the blast`() {
    assertNull(sut.refusal(world, caster, Vec3L(0, 0, 0), 2))

    tooHard = true

    assertEquals(OpError.MINE_ORE_TOO_HARD, sut.refusal(world, caster, Vec3L(0, 0, 0), 2))
  }

  private companion object {
    const val ACCOUNT = 3L
  }
}
