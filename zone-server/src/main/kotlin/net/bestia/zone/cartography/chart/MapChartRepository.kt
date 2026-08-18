package net.bestia.zone.cartography.chart

import org.springframework.data.jpa.repository.JpaRepository

interface MapChartRepository : JpaRepository<MapChart, Long> {

  fun findByItemInstanceId(itemInstanceId: Long): MapChart?

  /** Every chart among a set of held instances, for building the union a master can see. */
  fun findAllByItemInstanceIdIn(itemInstanceIds: Collection<Long>): List<MapChart>

  /**
   * Deletes the chart belonging to an instance that is being destroyed.
   *
   * A derived delete rather than a `@Query("DELETE ...")` one on purpose: a JPQL bulk delete bypasses cascade
   * and `orphanRemoval`. `MapChart` has no children today, so both would behave the same - but the next column
   * added here might, and the loud version of that bug is the one nobody notices.
   */
  fun deleteByItemInstanceId(itemInstanceId: Long)

  /** Clears the charts of a whole set of instances that are about to go - a deleted master's inventory. */
  fun deleteAllByItemInstanceIdIn(itemInstanceIds: Collection<Long>)
}
