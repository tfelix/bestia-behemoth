package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.battle.ecs.damage.TakenDamage
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test

class PlayerStructureDeathSystemTest {

  private val world: EcsWorld = testWorld()
  private val structures = mutableListOf<StructureEntry>()
  private val registry = mockk<PlayerStructureRegistry>(relaxed = true) {
    every { of(any()) } answers { structures.firstOrNull { it.id == firstArg<Long>() } }
  }
  private val salvage = mockk<StructureSalvage>(relaxed = true) {
    every { reads } returns emptySet()
    every { writes } returns emptySet()
  }

  private val system = PlayerStructureDeathSystem(
    structures = registry,
    worldService = mockk { every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0) },
    collision = mockk(relaxed = true),
    spills = emptyList(),
    chests = mockk(relaxed = true),
    events = mockk(relaxed = true),
    salvage = salvage,
  )

  @Test
  fun `another master who knocks a structure down salvages it`() {
    val stranger = player(STRANGER_ACCOUNT)
    destroyedForge(by = stranger)

    system.update(world, 0f)

    verify { salvage.salvage(world, structures.single(), AT, stranger, STRANGER_ACCOUNT) }
  }

  @Test
  fun `the owner salvages nothing from their own structure`() {
    destroyedForge(by = player(OWNER_ACCOUNT))

    system.update(world, 0f)

    verify(exactly = 0) { salvage.salvage(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a construction site salvages nothing`() {
    destroyedForge(by = player(STRANGER_ACCOUNT), buildSeconds = 20f)

    system.update(world, 0f)

    verify(exactly = 0) { salvage.salvage(any(), any(), any(), any(), any()) }
  }

  private fun player(accountId: Long): EntityId {
    return world.createEntity { id -> add(id, Account(accountId)) }
  }

  private fun destroyedForge(by: EntityId, buildSeconds: Float = 0f) {
    val entry = StructureEntry(STRUCTURE_ID, StaticEntityKind.FORGE, 3L, OWNER_ACCOUNT, AT, 0f, buildSeconds, buildSeconds)
    structures += entry

    world.createEntity { id ->
      add(id, Position.fromVec3(AT))
      add(id, PlayerStructureIdentity(STRUCTURE_ID, OWNER_ACCOUNT))
      add(id, TakenDamage().apply { addDamage(by, 500) })
      add(id, Dead())
    }
  }

  private companion object {
    const val STRUCTURE_ID = 42L
    const val OWNER_ACCOUNT = 7L
    const val STRANGER_ACCOUNT = 8L
    val AT = Vec3L(100, 100, 10)
  }
}
