package net.bestia.zoneserver.messages

import net.bestia.messages.proto.ComponentProtos
import net.bestia.zoneserver.entity.component.TemperatureComponent
import org.springframework.stereotype.Component

@Component
class TemperatureComponentConverter : MessageConverterOut<TemperatureComponent> {
  override fun convertToPayload(msg: TemperatureComponent): ByteArray {
    val builder = ComponentProtos.TemperatureComponent.newBuilder()
    val protoMsg = builder.apply {
      maxTolerableTemp = msg.maxTolerableTemperature
      minTolerableTemp = msg.minTolerableTemperature
      currentTemp = msg.currentTemperature
    }.build()

    return wrap { it.compTemperature = protoMsg }
  }

  override val fromMessage = TemperatureComponent::class.java
}