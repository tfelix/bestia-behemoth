package net.bestia.model.dao

import net.bestia.model.domain.PlayerBestia
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * DAO for accessing and manipulating the [PlayerBestia]s.
 *
 * @author Thomas Felix
 */
@Repository("playerBestiaDao")
interface PlayerBestiaDAO : CrudRepository<PlayerBestia, Long> {

  /**
   * Finds all [PlayerBestia]s for a given account ID.
   *
   * @param accId
   * Account ID to get all bestias.
   * @return A set of all found [PlayerBestia]s for this account.
   */
  @Query("FROM PlayerBestia pb WHERE pb.owner.id = :owner AND pb.isMaster = false")
  fun findPlayerBestiasForAccount(@Param("owner") accId: Long): Set<PlayerBestia>

  @Query("FROM PlayerBestia pb WHERE pb.owner.id = :owner AND pb.isMaster = true")
  fun findMasterBestiasForAccount(@Param("owner") accId: Long): PlayerBestia

  /**
   * Returns a master player bestia with the given name or null if no such
   * bestia could be found.
   *
   * @param name
   * The name of the master bestia to look for.
   * @return The found [PlayerBestia] or null.
   */
  @Query("FROM PlayerBestia pb WHERE pb.master != null AND pb.name = :name")
  fun findMasterBestiaWithName(@Param("name") name: String): PlayerBestia
}