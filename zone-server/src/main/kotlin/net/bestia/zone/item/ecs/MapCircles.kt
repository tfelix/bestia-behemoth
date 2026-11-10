package net.bestia.zone.item.ecs

import net.bestia.bnet.proto.InventoryComponentSMSGProto

/**
 * Where [Inventory] finds the circle an item copy draws on its holder's map. A higher slice that hands out such
 * copies installs itself at startup, so the item slice never names it.
 */
fun interface MapCircles {

  fun of(uniqueId: Long): Circle?

  /** In world metres. */
  data class Circle(val x: Double, val y: Double, val radius: Double) {

    fun toProto(): InventoryComponentSMSGProto.MapCircle {
      return InventoryComponentSMSGProto.MapCircle.newBuilder().setX(x).setY(y).setRadius(radius).build()
    }
  }

  companion object {
    /** Read on every inventory sync. Static because component messages are built without the container. */
    @Volatile
    var installed: MapCircles = MapCircles { null }
  }
}
