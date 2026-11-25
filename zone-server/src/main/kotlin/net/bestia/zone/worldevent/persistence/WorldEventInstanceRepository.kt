package net.bestia.zone.worldevent.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WorldEventInstanceRepository : JpaRepository<WorldEventInstance, Long> {

  fun findAllByStateIn(states: Collection<WorldEventInstance.State>): List<WorldEventInstance>

  fun findFirstByProvinceIndexAndEventIdOrderByStartedAtSecondDesc(provinceIndex: Int, eventId: String): WorldEventInstance?
}
