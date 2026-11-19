package net.bestia.zone.chronicle

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.chronicle.persistence.WorldFactRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("no-socket", "test")
@RecordApplicationEvents
class WorldFactStoreTest {

  @Autowired
  private lateinit var living: LivingChronicle

  @Autowired
  private lateinit var facts: WorldFactRepository

  @Autowired
  private lateinit var published: ApplicationEvents

  @AfterEach
  fun forget() {
    facts.deleteAll()
  }

  @Test
  fun `a recorded fact is kept as it was told, and announced`() {
    val actors = listOf(Actor(ActorType.ARTIFACT, 0), Actor(ActorType.SETTLEMENT, 3))

    val recorded = living.record(EventKind.ARTIFACT_ENSHRINED, actors, Vec2d(120.0, 80.5), LivingChronicle.Doer(9, "Ayla"))

    val stored = facts.findAllBySubjectTypeAndSubjectIndexOrderById(ActorType.ARTIFACT, 0).single()
    assertEquals(recorded.id, stored.id)
    assertEquals(actors, stored.actors)
    assertEquals(listOf(120.0, 80.5, 9L, "Ayla"), listOf(stored.whereX, stored.whereY, stored.byMasterId, stored.byMasterName))
    assertEquals(
      listOf(WorldFactRecordedEvent(recorded.id, EventKind.ARTIFACT_ENSHRINED, actors, LivingChronicle.Doer(9, "Ayla"))),
      published.stream(WorldFactRecordedEvent::class.java).toList(),
    )
  }
}
