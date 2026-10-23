package net.bestia.zone.travel.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RouteBlockageRepository : JpaRepository<RouteBlockage, RouteBlockage.Key>
