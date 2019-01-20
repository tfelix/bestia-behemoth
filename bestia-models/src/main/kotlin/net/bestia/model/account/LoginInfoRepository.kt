package net.bestia.model.account

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/**
 * Simple CRUD repository for accessing the [LoginInfo] objects.
 *
 * @author Thomas Felix
 */
@Repository
interface LoginInfoRepository : CrudRepository<LoginInfo, Long>
