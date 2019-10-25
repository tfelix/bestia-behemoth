package de.tfelix.bestia.ai.action;

import java.io.Serializable;

/**
 * The action references an entity and asks the engine to perform a single,
 * simple task. The actions will eventually lead to the fulfillment of a goal
 * which is a higher level planned activity.
 * 
 * It is considered that an action, although a small process, are indeed taking
 * up some time.
 * 
 * @author Thomas Felix
 *
 */
public abstract class Action implements Serializable {

	private static final long serialVersionUID = 1L;
	private final long agentId;
	private final long actionId;

	/**
	 * Ctor.
	 * 
	 * @param actionId
	 *            The unique id of the action.
	 * @param agentId
	 *            The ID of the agent.
	 */
	public Action(long actionId, long agentId) {

		this.actionId = actionId;
		this.agentId = agentId;
	}

	/**
	 * Each action has a unique ID so it can be identified by the engine later
	 * on.
	 * 
	 * @return Unique id of the action.
	 */
	public long getActionId() {
		return actionId;
	}

	/**
	 * Agend ID the action is applied on.
	 * 
	 * @return The agent id this action refers to.
	 */
	public long getAgentId() {
		return agentId;
	}

}