package net.bestia.zone.battle.attack

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.skillIdsOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FellingTest {

  private val world = testWorld()
  private val sut = Felling(skillIdsOf(SkillId.M_LUMBERJACK to 31L))

  @Test
  fun `a tree falls only to a felling tool or an axe`() {
    assertTrue(sut.canFell(world, holding(tool = ToolKind.FELLING)))
    assertTrue(sut.canFell(world, holding(weapon = WeaponType.GREATAXE)))
    assertFalse(sut.canFell(world, holding(weapon = WeaponType.SWORD)))
    assertFalse(sut.canFell(world, holding(tool = ToolKind.MINING)))
  }

  @Test
  fun `lumberjack five doubles what each blow does to the trunk`() {
    val feller = holding(tool = ToolKind.FELLING, lumberjackLevel = 5)

    assertEquals(200, sut.felled(world, feller, 100))
  }

  private fun holding(tool: ToolKind? = null, weapon: WeaponType? = null, lumberjackLevel: Int = 0) =
    world.createEntity { id ->
      add(id, Master(id))
      add(id, KnownSkills(mutableMapOf(31L to lumberjackLevel)))
      add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(heldTool = tool, heldWeaponType = weapon)))
    }
}
