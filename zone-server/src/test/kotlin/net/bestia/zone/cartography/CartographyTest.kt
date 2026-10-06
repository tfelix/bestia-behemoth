package net.bestia.zone.cartography

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A chart reveals terrain and settlements and can be traded, so where it may be drawn is the catalogued reach and
 * not whatever point the client sends.
 */
class CartographyTest {

  private val sut = Cartography(mockk(relaxed = true), mockk(relaxed = true), LineOfSightService())

  @Test
  fun `ground within reach can be charted`() {
    assertTrue(sut.isCastPossible(SkillContextFixture.skillCtx(BattleContextFixture.groundCtx())))
  }

  @Test
  fun `ground beyond the skill's range cannot be charted`() {
    val farAway = BattleContextFixture.groundCtx(targetPosition = Vec3L(500, 0, 0))

    assertFalse(sut.isCastPossible(SkillContextFixture.skillCtx(farAway)))
  }
}
