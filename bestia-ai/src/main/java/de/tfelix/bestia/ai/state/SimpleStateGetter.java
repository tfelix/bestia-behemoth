package de.tfelix.bestia.ai.state;

import de.tfelix.bestia.ai.Agent;

public class SimpleStateGetter extends StateGetter<SimpleStateData, SimpleState> {


	@Override
	public SimpleState getState(Agent agent, SimpleStateData data) {
		return new SimpleState(data.getCurrentState());
	}

}
