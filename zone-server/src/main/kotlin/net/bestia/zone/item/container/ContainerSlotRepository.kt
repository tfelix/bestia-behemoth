package net.bestia.zone.item.container

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ContainerSlotRepository : JpaRepository<ContainerSlot, Long> {

  /**
   * Frees every item promised to a trade, whatever trade that was.
   *
   * Only ever run at boot - see [net.bestia.zone.boot.TradeReservationCleanupBootRunner]. A bulk update
   * rather than a load-and-save because there is nothing to cascade: the marker is a column on the slot,
   * and no child row hangs off it.
   *
   * @return how many slots were freed
   */
  @Modifying
  @Query("UPDATE ContainerSlot s SET s.reservedByTradeId = null WHERE s.reservedByTradeId IS NOT NULL")
  fun clearAllTradeReservations(): Int
}
