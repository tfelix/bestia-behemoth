package net.bestia.model.bestia

import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.JpaRepository

@Repository
interface BestiaRepository : JpaRepository<Bestia, Int> {

  /**
   * Finds a bestia by its database name.
   *
   * @param databaseName
   * The bestia database name.
   * @return The found [Bestia] or NULL.
   */
  fun findByDatabaseName(databaseName: String): Bestia?

}
