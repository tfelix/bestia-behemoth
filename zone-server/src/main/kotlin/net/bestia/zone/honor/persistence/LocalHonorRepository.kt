package net.bestia.zone.honor.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** `findAll()` for the boot load: rows are bounded by what Masters did, not by the size of the world. */
interface LocalHonorRepository : JpaRepository<LocalHonor, Long>
