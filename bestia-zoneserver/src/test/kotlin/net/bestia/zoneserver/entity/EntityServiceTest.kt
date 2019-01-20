package net.bestia.zoneserver.entity

import akka.actor.PoisonPill
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.verify
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.model.bestia.Direction
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.component.PositionComponent
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import net.bestia.model.geometry.Point

@RunWith(MockitoJUnitRunner::class)
class EntityServiceTest {

  @Mock
  private lateinit var messageApi: MessageApi

  private lateinit var entityService: EntityService

  @Before
  fun setup() {
    entityService = EntityService(IdGeneratorService(), messageApi)
  }

  @Test
  fun `newEntity generated new entity with id not 0`() {
    val e = entityService.newEntity()
    Assert.assertNotEquals(0, e.id)
  }

  @Test
  fun `delete() sends PoisonPill to entity`() {
    val e = entityService.newEntity()
    entityService.delete(e)

    verify(messageApi).send(check {
      Assert.assertTrue(it is EntityEnvelope)
      it as EntityEnvelope
      Assert.assertEquals(e.id, it.entityId)
      Assert.assertTrue(it.content is PoisonPill)
    })
  }

  @Test
  fun `updateComponent send entity envelope with component`() {
    val posComp = PositionComponent(
        1337,
        isSightBlocking = false,
        facing = Direction.NORTH,
        shape = Point(10, 10)
    )

    entityService.updateComponent(posComp)

    verify(messageApi).send(check {
      Assert.assertTrue(it is EntityEnvelope)
      it as EntityEnvelope
      Assert.assertEquals(1337, it.entityId)
      Assert.assertEquals(posComp, it.content)
    })
  }
}