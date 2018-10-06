package net.bestia.model.domain

import java.io.Serializable

import javax.persistence.AttributeOverride
import javax.persistence.AttributeOverrides
import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.PrimaryKeyJoinColumn
import javax.persistence.Table
import javax.persistence.Transient

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

@Entity
@Table(name = "bestias")
class Bestia(
    @Id
    @JsonIgnore
    val id: Int = 0,

    /**
     * The database name.
     *
     * @return The database name.
     */
    @Column(name = "bestia_db_name", unique = true, nullable = false, length = 100)
    @JsonProperty("bdbn")
    val databaseName: String,

    @Column(name = "default_name", nullable = false, length = 100)
    @JsonIgnore
    var defaultName: String,

    @Enumerated(EnumType.STRING)
    @JsonProperty("ele")
    val element: Element,

    @JsonProperty("img")
    val image: String,

    @JsonProperty("sp")
    @AttributeOverrides(AttributeOverride(name = "type", column = Column(name = "visualType")))
    val spriteInfo: SpriteInfo,

    /**
     * Experience points gained if bestia was defeated.
     *
     * @return
     */
    @JsonIgnore
    val expGained: Int,

    /**
     * Returns the type of the bestia.
     *
     * @return The type of the bestia.
     */
    @Enumerated(EnumType.STRING)
    @JsonProperty("t")
    val type: BestiaType,

    @JsonIgnore
    val level: Int,

    @JsonIgnore
    val isBoss: Boolean = false,

    /**
     * Returns the status points of this bestia.
     *
     * @return The status points.
     */
    @JsonIgnore
    @Transient
    val statusPoints: StatusPoints,

    @Embedded
    @JsonIgnore
    val baseValues: BaseValues,

    /**
     * Script which will be attached to this bestia.
     */
    @JsonIgnore
    val scriptExec: String? = null
) : Serializable {

  override fun toString(): String {
    return String.format("Bestia[db: %s, id: %d, level: %d]", databaseName, id, level)
  }
}
