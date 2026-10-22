package net.bestia.zone.guild

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Written against the documented numbers rather than the formula, so a changed curve shows up here. */
class GuildLevelsTest {

  private val config = GuildConfig()
  private val sut = GuildLevels(config)

  @Test
  fun `the curve is Ragnarok Online's guild table scaled by the unit`() {
    // RO: 2M, 4M, 7M, 11M ... 56M at level 10; the unit stands in for the million.
    assertEquals(4_000L, sut.requiredExp(1))
    assertEquals(8_000L, sut.requiredExp(2))
    assertEquals(14_000L, sut.requiredExp(3))
    assertEquals(112_000L, sut.requiredExp(10))
  }

  @Test
  fun `level 10, where Guild Extension can be maxed, takes 348 thousand EXP from founding`() {
    assertEquals(348_000L, (1 until 10).sumOf { sut.requiredExp(it) })
  }

  @Test
  fun `nothing more is needed at the highest level`() {
    assertEquals(0L, sut.requiredExp(config.maxLevel))
  }

  @Test
  fun `EXP carries over every level it reaches`() {
    // 4000 to level 2, 8000 to level 3, 3000 left over.
    assertEquals(GuildLevels.Progress(level = 3, exp = 3_000, levelsGained = 2), sut.addExp(1, 0, 15_000))
  }

  @Test
  fun `EXP short of a level only adds up`() {
    assertEquals(GuildLevels.Progress(level = 1, exp = 3_999, levelsGained = 0), sut.addExp(1, 3_000, 999))
  }

  @Test
  fun `a guild at the top keeps nothing towards a level it cannot reach`() {
    val top = sut.addExp(config.maxLevel - 1, 0, Long.MAX_VALUE / 2)

    assertEquals(config.maxLevel, top.level)
    assertEquals(0L, top.exp)
  }

  @Test
  fun `Guild Extension seats four more per level, from 16 to 56`() {
    assertEquals(16, sut.maxMembers(0))
    assertEquals(20, sut.maxMembers(1))
    assertEquals(56, sut.maxMembers(10))
  }

  @Test
  fun `a guild has a skill point per level, founding included, less what it spent`() {
    assertEquals(1, sut.skillPoints(level = 1, spentSkillPoints = 0))
    assertEquals(0, sut.skillPoints(level = 1, spentSkillPoints = 1))
    assertEquals(3, sut.skillPoints(level = 5, spentSkillPoints = 2))
  }
}
