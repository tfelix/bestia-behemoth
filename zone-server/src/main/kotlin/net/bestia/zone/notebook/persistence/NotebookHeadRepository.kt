package net.bestia.zone.notebook.persistence

import jakarta.persistence.LockModeType
import net.bestia.zone.notebook.NotebookCollection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface NotebookHeadRepository : JpaRepository<NotebookHead, Long> {

  fun findByOwnerScopeAndOwnerIdAndCollection(
    ownerScope: NotebookCollection.Scope,
    ownerId: Long,
    collection: String,
  ): NotebookHead?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun findForUpdateByOwnerScopeAndOwnerIdAndCollection(
    ownerScope: NotebookCollection.Scope,
    ownerId: Long,
    collection: String,
  ): NotebookHead?
}
