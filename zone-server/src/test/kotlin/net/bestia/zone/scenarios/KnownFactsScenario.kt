package net.bestia.zone.scenarios

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.fact.FactRef
import net.bestia.zone.fact.FactService
import net.bestia.zone.fact.FactSource
import net.bestia.zone.fact.ecs.KnownFacts
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** A played master carries what it knows, so the tick can ask without the database. */
class KnownFactsScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var facts: FactService

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @AfterAll
  fun forget() {
    facts.deleteAllOf(masterId())
  }

  @Test
  fun `a master knows at spawn what it learned earlier, and at once what it learns while played`() {
    facts.learn(masterId(), LEARNED_EARLIER, FactSource.CARRIER)

    clientPlayer2.connect(masterId())
    val entityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)

    assertTrue(world.read { get(entityId, KnownFacts::class)!!.knows(LEARNED_EARLIER) })
    assertFalse(world.read { get(entityId, KnownFacts::class)!!.knows(LEARNED_IN_PLAY) })

    facts.learn(masterId(), LEARNED_IN_PLAY, FactSource.TOWNSFOLK)

    await { assertTrue(world.read { get(entityId, KnownFacts::class)!!.knows(LEARNED_IN_PLAY) }) }
  }

  private fun masterId(): Long {
    return testData.account2.masterIds.first()
  }

  private companion object {
    val LEARNED_EARLIER = FactRef.event(11)
    val LEARNED_IN_PLAY = FactRef(FactRef.Subject.ARTIFACT, 3, FactRef.Aspect.PLACE)
  }
}
