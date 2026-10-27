package net.bestia.zone.notebook.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.notebook.NotebookCollection
import org.hibernate.Length
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/** One thing a player keeps, as the JSON its [collection] reads. */
@Entity
@Table(
  name = "notebook_entry",
  uniqueConstraints = [UniqueConstraint(columnNames = ["owner_scope", "owner_id", "collection", "entry_key"])]
)
class NotebookEntry(
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "owner_scope", nullable = false, length = 16)
  var ownerScope: NotebookCollection.Scope = NotebookCollection.Scope.MASTER,

  /** A master id or an account id, as [ownerScope] says. */
  @Column(name = "owner_id", nullable = false)
  var ownerId: Long = 0,

  @Column(nullable = false, length = 40)
  var collection: String = "",

  @Column(name = "entry_key", nullable = false, length = 40)
  var entryKey: String = "",

  @Lob
  @Column(nullable = false, length = Length.LONG32)
  var body: String = "",

  /** The collection's [NotebookHead.revision] when this was last written. */
  @Column(nullable = false)
  var revision: Long = 0,

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.EPOCH,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
