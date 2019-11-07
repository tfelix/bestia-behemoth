package net.bestia.model.battle

import net.bestia.model.IntegrationTest
import net.bestia.model.bestia.Bestia
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.test.AttackFixture
import net.bestia.model.test.BestiaFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class AttackLevelRepositoryTest {
  @Autowired
  private lateinit var bestiaAttackRepository: BestiaAttackRepository

  @Autowired
  private lateinit var attackRepository: AttackRepository

  @Autowired
  private lateinit var bestiaRepository: BestiaRepository

  private lateinit var sut: BestiaAttack
  private lateinit var bestia: Bestia
  private lateinit var attack: Attack

  @BeforeEach
  fun setup() {
    attack = AttackFixture.createAttack(attackRepository = attackRepository)
    bestia = BestiaFixture.createBestia(bestiaRepository)
    sut = BestiaAttack(attack, bestia)
    bestiaAttackRepository.save(sut)
  }

  @Test
  fun getAllAttacksForBestia_existingId_list() {
    val atks = bestiaAttackRepository.getAllAttacksForBestia(bestia.id)
    assertNotNull(atks)
    assertEquals(1, atks.size.toLong())
  }

  @Test
  fun getAllAttacksForBestia_notExistingId_null() {
    val atks = bestiaAttackRepository.getAllAttacksForBestia(1337)
    assertTrue(atks.isEmpty())
  }
}
