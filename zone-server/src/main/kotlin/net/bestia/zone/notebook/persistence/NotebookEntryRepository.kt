package net.bestia.zone.notebook.persistence

import net.bestia.zone.notebook.NotebookCollection
import org.springframework.data.jpa.repository.JpaRepository

interface NotebookEntryRepository : JpaRepository<NotebookEntry, Long> {

  fun findAllByOwnerScopeAndOwnerIdAndCollectionOrderById(
    ownerScope: NotebookCollection.Scope,
    ownerId: Long,
    collection: String,
  ): List<NotebookEntry>

  fun findByOwnerScopeAndOwnerIdAndCollectionAndEntryKey(
    ownerScope: NotebookCollection.Scope,
    ownerId: Long,
    collection: String,
    entryKey: String,
  ): NotebookEntry?

  fun countByOwnerScopeAndOwnerIdAndCollection(
    ownerScope: NotebookCollection.Scope,
    ownerId: Long,
    collection: String,
  ): Long
}
