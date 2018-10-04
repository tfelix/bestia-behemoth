package net.bestia.model.dao

import org.springframework.stereotype.Repository

import net.bestia.model.domain.Bestia
import org.springframework.data.jpa.repository.JpaRepository

@Repository("bestiaDao")
interface BestiaDAO : JpaRepository<Bestia, Int> {

  /**
   * Finds a bestia by its database name.
   *
   * @param databaseName
   * The bestia database name.
   * @return The found [Bestia] or NULL.
   */
  fun findByDatabaseName(databaseName: String): Bestia?

}
