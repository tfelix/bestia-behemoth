package net.bestia.zoneserver.messages

import net.bestia.messages.proto.ComponentProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.proto.ModelProtos
import net.bestia.zoneserver.entity.component.PositionComponent
import org.springframework.stereotype.Component

@Component
class PositionComponentConverter :
    MessageConverterOut<PositionComponent>,
    MessageConverterIn<PositionComponent> {

  override fun convertToPayload(msg: PositionComponent): ByteArray {
    val pos = msg.shape.anchor
    val posC = ModelProtos.Vec3.newBuilder()
        .setX(pos.x)
        .setY(pos.y)
        .setZ(pos.z)

    return wrap {
      it.compPosition = ComponentProtos.PositionComponent.newBuilder()
          .setEntityId(msg.entityId)
          .setPosition(posC)
          .build()
    }
  }

  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): PositionComponent {
    TODO("Not implemented")
  }

  override val fromMessage = PositionComponent::class.java
  override val fromPayload = MessageProtos.Wrapper.PayloadCase.COMP_POSITION
}