package net.bestia.zone.ecs.battle

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.ecs.battle.exp.ExperienceGainCalculator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class ExperienceCalculatorTest {

  @MockK
  private lateinit var bestiaRepository: BestiaRepository

  private lateinit var calculator: ExperienceGainCalculator

  private val bestiaId = 42L

  @BeforeEach
  fun setUp() {
    calculator = ExperienceGainCalculator(bestiaRepository)
  }

  private fun givenBestiaExpReward(exp: Int) {
    // findByIdOrNull is a Kotlin extension over findById(id): Optional<T> — stub the real member.
    every { bestiaRepository.findById(bestiaId) } returns Optional.of(
      Bestia(
        id = bestiaId,
        identifier = "test_bestia",
        level = 1,
        experienceReward = exp,
        health = 10,
        mana = 10,
      )
    )
  }

  @Test
  fun `solo kill grants exactly the given exp`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 1,
    )

    assertEquals(100, result[1L])
  }

  @Test
  fun `solo kill splits proportionally to damage share`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 0.75f, 2L to 0.25f),
      attackingPlayerCount = 1,
    )

    assertEquals(75, result[1L])
    assertEquals(25, result[2L])
  }

  @Test
  fun `two players grant a 15 percent bonus on top of the base exp`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 0.5f, 2L to 0.5f),
      attackingPlayerCount = 2,
    )

    // total exp = floor(100 * 1.15) = 115, split evenly
    assertEquals(57, result[1L])
    assertEquals(57, result[2L])
  }

  @Test
  fun `bonus caps at 150 percent starting at 11 attacking players`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 11,
    )

    // total exp = floor(100 * 2.5) = 250
    assertEquals(250, result[1L])
  }

  @Test
  fun `bonus does not exceed the cap beyond 11 attacking players`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 50,
    )

    assertEquals(250, result[1L])
  }

  @Test
  fun `zero attacking players is treated like a solo kill`() {
    givenBestiaExpReward(100)

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 0,
    )

    assertEquals(100, result[1L])
  }

  @Test
  fun `unknown bestia yields no exp`() {
    every { bestiaRepository.findById(bestiaId) } returns Optional.empty()

    val result = calculator.calculate(
      killedBestiaId = bestiaId,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 1,
    )

    assertTrue(result.isEmpty())
  }
}
