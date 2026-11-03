package net.bestia.zone.townsfolk.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface TownsfolkDeathRepository : JpaRepository<TownsfolkDeath, Long>
