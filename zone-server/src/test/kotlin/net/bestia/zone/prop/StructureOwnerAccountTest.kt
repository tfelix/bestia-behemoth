package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals

/** Both shapes a structure takes in the world name the account that owns it. */
class StructureOwnerAccountTest {

  private val world = testWorld()

  @Test
  fun `a construction site carries its owner's account`() {
    val site = ConstructionSiteSpawner().spawn(world, entry(buildSeconds = 20f))

    assertEquals(OWNER_ACCOUNT, world.getOrThrow(site, PlayerStructureIdentity::class).ownerAccountId)
  }

  @Test
  fun `a finished station's site carries its owner's account`() {
    val propKinds = mockk<PropKindRegistry> {
      every { of(any()) } returns PropKindDto(kind = StaticEntityKind.WORKBENCH, maxHp = 100)
    }

    val site = PlayerStructureSource(mockk(), propKinds).siteOf(entry(buildSeconds = 0f))

    assertEquals(OWNER_ACCOUNT, site.ownerAccountId)
  }

  private fun entry(buildSeconds: Float): StructureEntry {
    return StructureEntry(
      id = 5L,
      kind = StaticEntityKind.WORKBENCH,
      ownerMasterId = 7L,
      ownerAccountId = OWNER_ACCOUNT,
      position = Vec3L(10, 10, 64),
      yaw = 0f,
      totalBuildSeconds = buildSeconds,
      remainingBuildSeconds = buildSeconds
    )
  }

  private companion object {
    const val OWNER_ACCOUNT = 3L
  }
}
