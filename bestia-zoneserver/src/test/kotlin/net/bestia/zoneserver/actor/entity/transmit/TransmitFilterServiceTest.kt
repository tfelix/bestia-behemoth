package net.bestia.zoneserver.actor.entity.transmit

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityId
import net.bestia.zoneserver.entity.component.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class TransmitFilterServiceTest {

  @ClientTransmitFilter(TestTransmitFilter::class)
  class ComponentWithFilter(override val entityId: Long) : Component

  class ComponentWithNoFilter(override val entityId: Long) : Component

  class TestTransmitFilter : TransmitFilter {
    override fun findTransmitCandidates(transmit: TransmitRequest): Set<EntityId> {
      return setOf(1)
    }

    override fun selectTransmitTargetAccountIds(candidates: Set<Entity>, transmit: TransmitRequest): Set<Long> {
      return candidates.map { it.id } as Set<Long>
    }
  }

  private val entity = Entity(1)
  private val componentNoFilter = ComponentWithNoFilter(1)
  private val componentFilter = ComponentWithFilter(1)

  private var sut: TransmitFilterService = TransmitFilterService(listOf(TestTransmitFilter()))

  @Test
  fun `component with no filter produces no candidates`() {
    val candidates = sut.findTransmitCandidates(TransmitRequest(componentNoFilter, entity))
    assertTrue(candidates.isEmpty())
  }

  @Test
  fun `component with filter produces candidates`() {
    val candidates = sut.findTransmitCandidates(TransmitRequest(componentFilter, entity))
    assertTrue(setOf(1) == candidates)
  }
}