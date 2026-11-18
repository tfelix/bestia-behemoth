package net.bestia.zone.fact.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.bestia.zone.fact.FactRef
import net.bestia.zone.fact.FactSource
import java.io.Serializable
import java.time.Instant

/** A fact a master has learned. A master learns a fact once; the first source stays. */
@Entity
@Table(name = "master_fact")
class MasterFact(
  @EmbeddedId
  var id: Key = Key(),

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var source: FactSource = FactSource.TOWNSFOLK,

  @Column(name = "learned_at", nullable = false)
  var learnedAt: Instant = Instant.EPOCH,
) {

  @Embeddable
  data class Key(
    /** No foreign key: masters live in a slice above this one. */
    @Column(name = "master_id", nullable = false)
    var masterId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var subject: FactRef.Subject = FactRef.Subject.EVENT,

    @Column(name = "subject_index", nullable = false)
    var subjectIndex: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    var aspect: FactRef.Aspect = FactRef.Aspect.TALE,
  ) : Serializable {

    constructor(masterId: Long, fact: FactRef) : this(masterId, fact.subject, fact.index, fact.aspect)

    fun fact(): FactRef {
      return FactRef(subject, subjectIndex, aspect)
    }
  }
}
