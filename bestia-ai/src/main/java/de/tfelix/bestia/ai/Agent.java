package de.tfelix.bestia.ai;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

import de.tfelix.bestia.ai.action.Action;
import de.tfelix.bestia.ai.action.Goal;
import de.tfelix.bestia.ai.state.State;
import de.tfelix.bestia.ai.state.StateData;

/**
 * Holds the current internal state of the agent.
 * 
 * @author Thomas Felix
 *
 */
public class Agent implements Serializable {

	private static final long serialVersionUID = 0L;
	
	private long id;
	
	private State state;
	private StateData stateData;
	
	private String group;
	private Goal goal;
	private Queue<Action> currentActionQueue = new LinkedList<>();
	
	public Agent(long id) {

		this.id = id;
	}

	/**
	 * The group of this AI actors. AI logic can be grouped for certain actors
	 * to allow different AI settings.
	 * 
	 * @return
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * Gets the ID of the agent. The ID must be unique throughout the entire
	 * lifetime.
	 * 
	 * @return The ID of the agent.
	 */
	public long getId() {
		return id;
	}

	public State getState() {
		return state;
	}

	/**
	 * Sets the internal state.
	 * 
	 * @param state
	 *            The new state.
	 */
	public void setState(State state) {
		this.state = state;
	}

	@Override
	public String toString() {
		return String.format("AIAgent[state: %s]", state);
	}

	public void setGoal(Goal goal) {
		
		this.goal = goal;
	}

	public Goal getGoal() {
		return goal;
	}

	public StateData getStateData() {
		return stateData;
	}

}
