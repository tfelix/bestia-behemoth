package net.bestia.messages.ui

import java.io.Serializable

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TranslationItem @JsonCreator
constructor(
    @get:JsonProperty("k")
    @JsonProperty("k")
    val key: String,
    @get:JsonProperty("v")
    @JsonProperty("v")
    val value: String?
) : Serializable {

  @JsonCreator
  constructor(@JsonProperty("k") key: String) : this(key, null)
}