package net.bestia.zone.casting.scripts.master

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.TripleBlow
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrawlerFistSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID
  private val tripleBlow = mockk<TripleBlow>()
  private val chainCombo = ChainCombo(LineOfSightService(), tripleBlow)

  @Test
  fun `Chain Combo follows a Triple Blow with four blows that share 300 to 500 percent ATK`() {
    world.foes += target
    world.tripleBlowOpen = true

    assertTrue(chainCombo.isCastPossible(ctx(level = 5)))
    chainCombo.execute(ctx(level = 5))

    assertEquals(listOf(125, 125, 125, 125), world.strikes.map { it.second })
    assertTrue(world.strikes.all { it.first == target })
    assertEquals(listOf(300, 350, 400, 450, 500), (1..5).map { ChainCombo.hitPercents(it).sum() })
  }

  @Test
  fun `one Triple Blow allows one Chain Combo`() {
    world.foes += target
    world.tripleBlowOpen = true

    chainCombo.execute(ctx(level = 1))
    chainCombo.execute(ctx(level = 1))

    assertEquals(ChainCombo.HITS, world.strikes.size)
    assertFalse(chainCombo.isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `without a Triple Blow just before Chain Combo is refused at the button`() {
    val ecs = testWorld()
    val brawler = ecs.createEntity { }
    every { tripleBlow.mayFollowUp(ecs, brawler) } returns false

    assertEquals(OpError.SKILL_NEEDS_TRIPLE_BLOW, chainCombo.checkCastStart(ecs, brawler, 1, emptyList()))

    every { tripleBlow.mayFollowUp(ecs, brawler) } returns true
    assertNull(chainCombo.checkCastStart(ecs, brawler, 1, emptyList()))
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
