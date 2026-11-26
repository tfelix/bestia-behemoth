package net.bestia.api.bugreport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "bug_report_file")
class BugReportFile(
  @Column(name = "report_id", nullable = false)
  val reportId: Long,

  /** The file's name in the reports repo, e.g. `client.log`. */
  @Column(nullable = false, length = 64)
  val name: String,

  @Column(nullable = false, columnDefinition = "LONGBLOB")
  val content: ByteArray,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
