package net.bestia.zoneserver.battle.damage

import io.mockk.junit5.MockKExtension
import net.bestia.zoneserver.battle.EntityBattleContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@ExtendWith(MockKExtension::class)
internal class MeleePhysicalDamageCalculatorTest {

  @Mock
  private lateinit var randomMock: Random

  private lateinit var sut: MeleePhysicalDamageCalculator
  private val battlCtx = EntityBattleContext.test()

  @BeforeEach
  fun setup() {
    sut = MeleePhysicalDamageCalculator(randomMock)
  }

  @Test
  fun `doAttack calculates the correct damage`() {
    val damage = sut.calculateDamage(battlCtx)

  }
}