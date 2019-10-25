package de.tfelix.bestia.ai.provider;

import java.util.HashMap;
import java.util.Map;

import de.tfelix.bestia.ai.Agent;

/**
 * The agents are just held in memory. This works well with a small number of AI
 * agents.
 * 
 * @author Thomas Felix
 *
 */
public class MemoryAgentProvider implements AgentProvider {
	
	private final Map<Long, Agent> agents = new HashMap<>();

	public void saveAgent(Agent agent) {
		
		agents.put(agent.getId(), agent);
	}

	public Agent findAgend(long id) {
		
		return agents.get(id);
	}

}
