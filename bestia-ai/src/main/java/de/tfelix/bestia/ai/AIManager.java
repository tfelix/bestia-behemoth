package de.tfelix.bestia.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tfelix.bestia.ai.action.Goal;
import de.tfelix.bestia.ai.config.GroupConfig;
import de.tfelix.bestia.ai.provider.AgentProvider;
import de.tfelix.bestia.ai.provider.MemoryAgentProvider;
import de.tfelix.bestia.ai.state.State;
import de.tfelix.bestia.ai.state.StateGetter;
import de.tfelix.bestia.ai.state.StateTransformer;

/**
 * This is the central API of the AI library. Incoming calls to control the AI
 * actors tick rate or to propagate events from the game engine towards the
 * actors are managed by this manager instance.
 * 
 * Configuration and settings is also handled by this manager.
 * 
 * @author Thomas Felix
 *
 */
public class AIManager {

	private static final Logger LOG = LoggerFactory.getLogger(AIManager.class);

	private final AgentProvider agentProvider;
	private final GameEngine externalEngine;
	private final Map<String, GroupConfig> groupConfigs = new HashMap<>();

	public AIManager(GameEngine externalEngine) {

		this.externalEngine = Objects.requireNonNull(externalEngine);
		// TODO das hier austauschbar machen.
		this.agentProvider = new MemoryAgentProvider();
	}

	/**
	 * This ticks the agent AI logic and performs a reevaluation if the
	 * environment has changed.
	 * 
	 * @param id
	 *            Agent id to be ticked.
	 */
	public void tickAgent(long id) {
		// FIXME This is work in progress
		/*
		// Retrieve the agent.
		final Agent currentAgent = agentProvider.findAgend(id);

		if (currentAgent == null) {
			LOG.warn("Agent with id {} was not found by provider.", id);
			return;
		}

		// Retrieve its group and setup the tools for this group of AI agents.

		StateGetter stateGetter = null;

		State currentState = stateGetter.retrieveState(currentAgent);

		// The next state depends upon the current state, the environment and
		// the transforming function.
		StateTransformer transformer = getStateTransformer(currentAgent.getGroup(), currentState);

		// Depending on the environment setting re-evaluate the state.
		State newState = transformer.evaluateState();

		if (newState.getSuggestedTickrateMs() != currentState.getSuggestedTickrateMs()) {
			// Re-suggest new tickrate.
			externalEngine.suggestTickRate(id, newState.getSuggestedTickrateMs());
		}

		// Save the state.
		currentAgent.setState(newState);

		evaluateGoals(currentAgent);

		if (currentAgent.getGoal() != null) {
			evaluateActions(currentAgent);
		}

		// Save the new agent.
		agentProvider.saveAgent(currentAgent);
		*/
	}

	/**
	 * Find a list of actions usable to fullfill the given goal of the current
	 * agent.
	 * 
	 * @param currentAgent
	 */
	private void evaluateActions(Agent currentAgent) {
		
		// Get a list of possible actions.
		
		// Solve them in order to perform a goal.
		
	}

	/**
	 * Evaluates the new list of goals for this agent and leaves it up to the
	 * engine to perform this goals.
	 * 
	 * @param currentAgent
	 */
	private void evaluateGoals(Agent currentAgent) {
		GoalFinder goalFinder = null;
		GoalEvaluater goalEvaluator = null;

		Set<Goal> possibleGoals = goalFinder.possibleGoals(agentProvider);
		List<Goal> sortedGoals = goalEvaluator.evaluateGoals(possibleGoals);

		final Goal choosenGoal = sortedGoals.get(0);
		currentAgent.setGoal(choosenGoal);
	}

	private StateTransformer getStateTransformer(String group, State currentState) {
		// TODO Auto-generated method stub
		return null;
	}

	public void addGroupConfig(GroupConfig groupConfig) {
		Objects.requireNonNull(groupConfig);
		groupConfigs.put(groupConfig.getGroupName(), groupConfig);
	}

}
