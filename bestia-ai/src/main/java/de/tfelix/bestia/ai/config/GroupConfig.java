package de.tfelix.bestia.ai.config;

import java.util.Objects;

import de.tfelix.bestia.ai.state.StateTransformer;

public class GroupConfig {
	
	private final String groupName;
	
	private StateTransformer transformer;
	
	public GroupConfig(String groupName, StateTransformer transformer) {
		
		this.groupName = Objects.requireNonNull(groupName);
		this.transformer = Objects.requireNonNull(transformer);
	}

	public String getGroupName() {
		return groupName;
	}
	
	public StateTransformer getTransformer() {
		return transformer;
	}
}
