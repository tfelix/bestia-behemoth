package de.tfelix.bestia.ai.state;

import java.io.Serializable;

public abstract class State implements Serializable {
	
	private static final long serialVersionUID = 1L;
	public static final int DEFAULT_TICKRATE_MS = -1;
	
	public int getSuggestedTickrateMs() {
		return DEFAULT_TICKRATE_MS;
	}

}
