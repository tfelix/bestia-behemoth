package net.bestia.zone.economy.shop

import net.bestia.zone.util.EntityId

/** Who keeps which business. The townsfolk slice knows its people, so it implements this. */
interface MerchantDirectory {

  /** The business [entityId] works in, or null for anyone who keeps none. */
  fun businessOf(entityId: EntityId): String?
}
