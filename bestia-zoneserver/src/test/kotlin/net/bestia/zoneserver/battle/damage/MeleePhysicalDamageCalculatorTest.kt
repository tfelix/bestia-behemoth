package net.bestia.zoneserver.battle.damage

import net.bestia.zoneserver.battle.EntityBattleContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
internal class MeleePhysicalDamageCalculatorTest {

  @Mock
  private lateinit var randomMock: Random

  private lateinit var sut: MeleePhysicalDamageCalculator
  private val battlCtx = EntityBattleContext.test()

  @Before
  fun setup() {
    sut = MeleePhysicalDamageCalculator(randomMock)
  }

  @Test
  fun `doAttack calculates the correct damage`() {
    val damage = sut.calculateDamage(battlCtx)

  }
}