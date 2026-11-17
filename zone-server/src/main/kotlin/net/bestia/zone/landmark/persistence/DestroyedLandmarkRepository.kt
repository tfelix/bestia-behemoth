package net.bestia.zone.landmark.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface DestroyedLandmarkRepository : JpaRepository<DestroyedLandmark, DestroyedLandmark.Key>
