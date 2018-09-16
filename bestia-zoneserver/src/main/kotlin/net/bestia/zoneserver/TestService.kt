package net.bestia.zoneserver

import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import net.bestia.entity.component.TestComponent
import net.bestia.messages.ComponentUpdate
import net.bestia.messages.entity.EntityEnvelope
import org.springframework.stereotype.Service

data class UpdateTestComponent(
        val addedText: String
)

data class ComponentEnvelope2<T : Component>(
        val componentClass: Class<T>,
        val content: Any
)

@Service
class TestService(
        private val entityService: EntityService,
        private val messageApi: AkkaMessageApi2
) {

  fun addComponent(content: String): Long {
    val entity = entityService.newEntity()
    val myComp = TestComponent(1337, content)
    myComp.entityId = entity.id

    val componentMsg = ComponentUpdate(myComp)
    val entityEnvelope = EntityEnvelope(entity.id, componentMsg)
    messageApi.send(entityEnvelope)

    return entity.id
  }

  fun updateComponent(entityId: Long, addedText: String) {
    val update = UpdateTestComponent(addedText)
    val envelope = ComponentEnvelope2(TestComponent::class.java, update)
    val entityEnvelope = EntityEnvelope(entityId, envelope)
    messageApi.send(entityEnvelope)
  }
}