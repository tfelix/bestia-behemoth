package bestia.model.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import bestia.model.domain.LoginInfo;

/**
 * Simple CRUD repository for accessing the {@link LoginInfo} objects.
 * 
 * @author Thomas Felix
 *
 */
@Repository
public interface LoginInfoDAO extends CrudRepository<LoginInfo, Long> {

}
