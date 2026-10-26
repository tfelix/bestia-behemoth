package net.bestia.zone.crafting

import net.bestia.zone.scenarios.ScenarioDataSetup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class KnownRecipeStoreTest {

  @Autowired
  private lateinit var store: KnownRecipeStore

  @Autowired
  private lateinit var testFixture: ScenarioDataSetup.TestFixture

  @Test
  fun `a learned blueprint is remembered once and loads again`() {
    val masterId = testFixture.account1.masterIds.first()
    val recipeId = 9_001L

    assertTrue(store.learn(masterId, recipeId), "the first time teaches it")
    assertFalse(store.learn(masterId, recipeId), "the second time teaches nothing")
    assertTrue(recipeId in store.load(masterId))
  }
}
