package net.bestia.model.domain

import java.io.Serializable

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.Transient

/**
 * This will save script variables to the database. These variables can be used
 * for several purposes:
 *
 *  * NPC-Player Bestia Variables
 *  * NPC-Account Variables
 *  * NPC-Only Variables
 *  * Zone Global Variables
 *  * Server Global Variables
 * *NPC-Player Bestia** These variables will relate to bestias to certain
 * NPCs/Entities.
 *
 *
 * @author Thomas Felix <thomas.felix></thomas.felix>@tfelix.de>
 */
@Entity
@Table(name = "script_vars", indexes = [Index(name = "name_id_key", columnList = "script_key", unique = false)])
data class ScriptVar(
    @Id
    val id: Long = 0,

    @Column(name = "script_key", nullable = false)
    val scriptKey: String,

    var data: String
) : Serializable
