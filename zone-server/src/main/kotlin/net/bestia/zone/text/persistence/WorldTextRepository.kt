package net.bestia.zone.text.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WorldTextRepository : JpaRepository<WorldText, WorldText.Key>
