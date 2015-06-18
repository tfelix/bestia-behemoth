package net.bestia.messages.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract class LocationMixIn {

	@JsonProperty("m")
	abstract String getMapDbName();
}
