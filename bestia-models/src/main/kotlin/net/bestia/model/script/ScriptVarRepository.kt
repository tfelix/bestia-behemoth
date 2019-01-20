package net.bestia.model.script

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

import net.bestia.model.script.ScriptVar

/**
 * DAO to access the [ScriptVar] models.
 *
 * @author Thomas Felix
 */
@Repository
interface ScriptVarRepository : CrudRepository<ScriptVar, Long> {

  /**
   * Finds a script var by looking for its unique script var key.
   *
   */
  fun findByScriptKey(key: String): ScriptVar
}
