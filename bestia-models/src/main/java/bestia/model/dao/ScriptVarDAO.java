package bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import bestia.model.domain.ScriptVar;

/**
 * DAO to access the {@link ScriptVar} models.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Repository("scriptVarDao")
public interface ScriptVarDAO extends CrudRepository<ScriptVar, Long> {

	/**
	 * Finds a script var by looking for its unique script var key.
	 * 
	 * @param key
	 * @return
	 */
	public ScriptVar findByScriptKey(String key);

}
