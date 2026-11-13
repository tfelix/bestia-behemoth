package net.bestia.zone.prop

import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.skillIdsOf
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.ecs.KnownSkills
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HerbPickingTest {

  private val sut = HerbPicking(
    skillIds = skillIdsOf(SkillId.M_HERBALISM to HERBALISM),
    items = mockk { every { baseLevelOf(any()) } answers { LEVELS[firstArg()] } },
  )
  private val world = testWorld()

  @Test
  fun `untrained, only the wild herb can be picked`() {
    val picker = master(herbalism = 0)

    assertTrue(sut.mayPick(world, picker, StaticEntityKind.HERB, WILD))
    assertFalse(sut.mayPick(world, picker, StaticEntityKind.MEDICINAL_HERB, MEDICINAL))
  }

  @Test
  fun `each level opens twenty more herb levels, the top level every herb`() {
    assertEquals(listOf(10, 20, 40, 60, 80, Int.MAX_VALUE), (0..5).map { HerbPicking.maxHerbLevel(it) })
    assertTrue(sut.mayPick(world, master(herbalism = 5), StaticEntityKind.GHOST_HERB, GHOST))
    assertFalse(sut.mayPick(world, master(herbalism = 4), StaticEntityKind.GHOST_HERB, GHOST))
  }

  @Test
  fun `a bestia picks with its master's Herbalism`() {
    master(herbalism = 1)
    val bestia = world.createEntity { id -> add(id, OwnedBestia(MASTER_ID, 3L)) }

    assertTrue(sut.mayPick(world, bestia, StaticEntityKind.MEDICINAL_HERB, MEDICINAL))
  }

  @Test
  fun `anything that is not a herb is not gated`() {
    assertTrue(sut.mayPick(world, master(herbalism = 0), StaticEntityKind.MANA_CRYSTAL_SMALL, GHOST))
  }

  private fun master(herbalism: Int) = world.createEntity { id ->
    add(id, Master(MASTER_ID))
    add(id, KnownSkills(mutableMapOf(HERBALISM to herbalism)))
  }

  private companion object {
    const val HERBALISM = 23L
    const val MASTER_ID = 42L
    const val WILD = 23L
    const val MEDICINAL = 328L
    const val GHOST = 332L
    val LEVELS = mapOf(WILD to 1, MEDICINAL to 20, GHOST to 100)
  }
}
