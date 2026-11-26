package net.bestia.zone.doomsday.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WorldLegacyRepository : JpaRepository<WorldLegacy, Long>
