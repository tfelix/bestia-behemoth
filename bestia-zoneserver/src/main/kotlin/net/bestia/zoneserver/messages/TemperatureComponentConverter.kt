package net.bestia.zoneserver.messages

import net.bestia.messages.proto.ComponentsProto
import net.bestia.zoneserver.entity.component.TemperatureComponent
import org.springframework.stereotype.Component

@Component
class TemperatureComponentConverter : OnlyToClientConverter<TemperatureComponent>() {

  override fun convertFromBestia(msg: TemperatureComponent): ByteArray {
    val builder = ComponentsProto.TemperatureComponent.newBuilder()
    val protoMsg = builder.apply {
      maxTolerableTemp = msg.maxTolerableTemperature
      minTolerableTemp = msg.minTolerableTemperature
      currentTemp = msg.currentTemperature
    }.build()

    return protoMsg.toByteArray()
  }

  override val canConvert = TemperatureComponent::class.java
}