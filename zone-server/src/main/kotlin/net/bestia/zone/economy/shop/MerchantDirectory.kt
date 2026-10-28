package net.bestia.zone.economy.shop

import net.bestia.zone.util.EntityId

/** Who keeps which business. The townsfolk slice knows its people, so it implements this. */
interface MerchantDirectory {

  /** The person behind [entityId], or null for anything that is not a townsperson. */
  fun merchantOf(entityId: EntityId): Merchant?
}
