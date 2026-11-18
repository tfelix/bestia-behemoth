package net.bestia.zone.fact

import net.bestia.zone.fact.persistence.MasterFactRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class FactServiceTest {

  @Autowired
  private lateinit var facts: FactService

  @Autowired
  private lateinit var rows: MasterFactRepository

  @AfterEach
  fun forget() {
    facts.deleteAllOf(READER)
    facts.deleteAllOf(OTHER)
  }

  @Test
  fun `a fact is learned once and its first source stays`() {
    assertTrue(facts.learn(READER, BATTLE, FactSource.CARRIER))
    assertFalse(facts.learn(READER, BATTLE, FactSource.TOWNSFOLK))

    assertEquals(setOf(BATTLE), facts.factsOf(READER))
    assertEquals(FactSource.CARRIER, rows.findAllByIdMasterId(READER).single().source)
  }

  @Test
  fun `the aspects of one record are facts of their own, and each master learns for itself`() {
    facts.learn(READER, KEEPER_NAME, FactSource.CARRIER)
    facts.learn(READER, KEEPER_FATE, FactSource.TOWNSFOLK)
    facts.learn(OTHER, KEEPER_NAME, FactSource.REWARD)

    assertEquals(setOf(KEEPER_NAME, KEEPER_FATE), facts.factsOf(READER))
    assertEquals(setOf(KEEPER_NAME), facts.factsOf(OTHER))
  }

  @Test
  fun `a deleted master's facts are gone`() {
    facts.learn(READER, BATTLE, FactSource.TOWNSFOLK)

    facts.deleteAllOf(READER)

    assertEquals(emptySet(), facts.factsOf(READER))
  }

  private companion object {
    // No master rows are needed, and ids this high cannot meet the fixture's masters.
    const val READER = 9_100_001L
    const val OTHER = 9_100_002L

    val BATTLE = FactRef.event(412)
    val KEEPER_NAME = FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.NAME)
    val KEEPER_FATE = FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.FATE)
  }
}
