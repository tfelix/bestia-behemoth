package net.bestia.zone.ecs.persistence.persisters

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.Optional
import kotlin.test.assertEquals

/**
 * What a master takes with it out of the world - in particular that leaving while dead resolves the
 * respawn on the way out, rather than storing the spot it was killed on.
 */
class MasterEntityPersisterTest {

  private val masterId = 5L
  private val savePoint = Vec3L(10, 20, 30)

  private val row = Master(
    account = mockk(relaxed = true),
    name = "Tester",
    hairColor = Color.BLACK,
    skinColor = Color.WHITE,
    hair = Hairstyle.entries.first(),
    face = Face.entries.first(),
    body = BodyType.entries.first(),
  ).also { it.spawnPosition = savePoint }

  private val repository = mockk<MasterRepository> {
    every { findById(any()) } returns Optional.of(row)
    every { save(any()) } returns row
  }

  private val sut = MasterEntityPersister(repository)

  private fun World.master(dead: Boolean) = createEntity { eid ->
    add(eid, MasterComponent(masterId, "Tester"))
    add(eid, Position(70, 71, 72))
    add(eid, Health(current = if (dead) 0 else 123, max = 200))
    if (dead) add(eid, Dead())
  }

  @Test
  fun `a living master keeps where it stood and the health it had`() {
    val world = testWorld()
    val id = world.master(dead = false)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(Vec3L(70, 71, 72), row.currentPosition)
    assertEquals(123, row.currentHealth)
  }

  @Test
  fun `a master that left dead is stored at its save point with one hit point`() {
    val world = testWorld()
    val id = world.master(dead = true)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(savePoint, row.currentPosition)
    assertEquals(1, row.currentHealth)
  }
}
