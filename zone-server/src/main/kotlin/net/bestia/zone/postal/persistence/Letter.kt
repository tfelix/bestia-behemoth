package net.bestia.zone.postal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * A letter in a master's mailbox. The sender's name is kept as it was written, so a letter still reads right
 * after its sender is renamed or deleted.
 */
@Entity
@Table(name = "letter", indexes = [Index(columnList = "recipient_master_id"), Index(columnList = "expires_at")])
class Letter(
  @Column(name = "recipient_master_id", nullable = false)
  val recipientMasterId: Long,

  @Column(name = "sender_master_id", nullable = false)
  val senderMasterId: Long,

  @Column(name = "sender_name", nullable = false)
  val senderName: String,

  @Column(nullable = false, length = MAX_SUBJECT_LENGTH)
  val subject: String,

  @Column(nullable = false, length = MAX_BODY_LENGTH)
  val body: String,

  @Column(name = "sent_at", nullable = false)
  val sentAt: Instant,

  @Column(name = "expires_at", nullable = false)
  val expiresAt: Instant,

  // READ is a reserved word in MariaDB.
  @Column(name = "is_read", nullable = false)
  var read: Boolean = false,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  companion object {
    /** Must match the client's letter form and the app's. */
    const val MAX_SUBJECT_LENGTH = 60
    const val MAX_BODY_LENGTH = 1000
  }
}
