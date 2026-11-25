package net.bestia.zone.battle.status

import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Who the wild notice from twice as far as they see: the wearers of the Rafflesia Queen's Stench. Few or none, so the
 * perception checks them by hand rather than widening its search for everyone.
 */
@Component
class FarNoticed {

  /** Replaced whole once a second; read by the perception in a later phase of the same tick. */
  @Volatile
  var wearers: List<EntityId> = emptyList()
}
