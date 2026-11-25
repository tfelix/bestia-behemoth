package net.bestia.zone.worldevent.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface EventContributionRepository : JpaRepository<EventContribution, Long>
