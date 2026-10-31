package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.Random

class FlavourSkillsTest {

  @Test
  fun `proclaim says one of its row's lines`() {
    val world = RecordingSkillWorld()
    val skill = SkillContextFixture.skill().apply { lines = mutableListOf("DOOM!", "More doom!") }

    Proclaim(Random(3)).execute(SkillContextFixture.skillCtx(world = world, skill = skill))

    assertEquals(1, world.spoken.size)
    assertEquals(true, world.spoken.single() in skill.lines)
  }

  @Test
  fun `proclaim with nothing to say cannot be cast`() {
    assertFalse(Proclaim().isCastPossible(SkillContextFixture.skillCtx()))
  }

  @Test
  fun `kindle only sets the grass alight`() {
    val world = RecordingSkillWorld()
    val aimedAt = Vec3L(3, 0, 0)

    Kindle(LineOfSightService()).execute(
      SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(targetPosition = aimedAt), world)
    )

    assertEquals(listOf(RecordingSkillWorld.Ignition(aimedAt, 0L)), world.ignitions)
    assertEquals(emptyList<Any>(), world.spawnedAreaEffects)
  }
}
