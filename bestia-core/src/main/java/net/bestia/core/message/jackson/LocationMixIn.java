package net.bestia.core.message.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;

abstract class LocationMixIn {

	@JsonProperty("m")
	abstract String getMapDbName();
}
