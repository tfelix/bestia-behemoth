package net.bestia.zoneserver.messages

import net.bestia.messages.component.ComponentMessageProto
import net.bestia.zoneserver.entity.component.TemperatureComponent
import org.springframework.stereotype.Component

@Component
class TemperatureComponentConverter : MessageConverter<ComponentMessageProto.TemperatureComponent, TemperatureComponent>() {
  override fun convertFromWire(proto: ComponentMessageProto.TemperatureComponent): TemperatureComponent {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun convertFromBestia(msg: TemperatureComponent): ComponentMessageProto.TemperatureComponent {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override val canConvert = Pair(ComponentMessageProto.TemperatureComponent::class.java, TemperatureComponent::class.java)
}