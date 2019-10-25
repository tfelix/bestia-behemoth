package de.tfelix.bestia.ai.state;

import de.tfelix.bestia.ai.Agent;

/**
 * The {@link StateRetriever} retrieves the current, concrete state from an
 * agent into a usable single state for the next step in evaluating AI action.
 * 
 * @author Thomas Felix
 *
 */
public abstract class StateGetter<T extends StateData, S extends State> {
	
	public abstract S getState(Agent agent, T data);
	
}
