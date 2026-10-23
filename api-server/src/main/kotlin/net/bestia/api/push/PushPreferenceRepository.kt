package net.bestia.api.push

import org.springframework.data.jpa.repository.JpaRepository

interface PushPreferenceRepository : JpaRepository<PushPreference, Long>
