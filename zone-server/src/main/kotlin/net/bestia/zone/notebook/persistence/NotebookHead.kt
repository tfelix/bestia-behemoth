package net.bestia.zone.notebook.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.notebook.NotebookCollection
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * How many times one owner's collection has been written. Locked for every write, so the writes to a collection
 * are numbered one after the other and a client that missed one can tell.
 */
@Entity
@Table(
  name = "notebook_head",
  uniqueConstraints = [UniqueConstraint(columnNames = ["owner_scope", "owner_id", "collection"])]
)
class NotebookHead(
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "owner_scope", nullable = false, length = 16)
  var ownerScope: NotebookCollection.Scope = NotebookCollection.Scope.MASTER,

  @Column(name = "owner_id", nullable = false)
  var ownerId: Long = 0,

  @Column(nullable = false, length = 40)
  var collection: String = "",

  @Column(nullable = false)
  var revision: Long = 0,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
