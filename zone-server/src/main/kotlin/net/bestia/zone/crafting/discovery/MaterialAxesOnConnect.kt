package net.bestia.zone.crafting.discovery

import net.bestia.zone.message.SMSG
import net.bestia.zone.world.stream.ConnectMessageSource
import org.springframework.stereotype.Component

/** Gives every client the materials' axes on connect, so item tooltips can show them from the start. */
@Component
class MaterialAxesOnConnect(
  private val materialAxes: MaterialAxes,
) : ConnectMessageSource {

  override fun connectMessages(): List<SMSG> = listOf(MaterialAxesSMSG(materialAxes.all()))
}
