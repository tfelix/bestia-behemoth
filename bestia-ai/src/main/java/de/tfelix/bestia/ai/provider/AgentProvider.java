package de.tfelix.bestia.ai.provider;

import de.tfelix.bestia.ai.Agent;

/**
 * The AI system must have a way to permanently access a huge number of agents.
 * It might not be possible to hold them in place the whole time. A provider is
 * therefore needed to retrieve them on demand from whatever kind of storage
 * system.
 * 
 * @author Thomas Felix
 *
 */
public interface AgentProvider {

	void saveAgent(Agent agent);

	/**
	 * Finds the agent in the system and retrieves it. The implementation might
	 * return null if the agent was not found in the underlying storage system.
	 * 
	 * @param id
	 *            The ID of the agent to be found.
	 * @return The found agent or NULL if the ID did not match any agent.
	 */
	Agent findAgend(long id);

}
