package de.tfelix.bestia.ai.action;

import de.tfelix.bestia.ai.AIPoint;

public class MoveAction extends Action {
	
	private static final long serialVersionUID = 1L;
	private AIPoint destination;
	
	public MoveAction(long actionId, long agentId) {
		super(actionId, agentId);
		// no op.
	}
	
	public AIPoint getDestination() {
		return destination;
	}

}
