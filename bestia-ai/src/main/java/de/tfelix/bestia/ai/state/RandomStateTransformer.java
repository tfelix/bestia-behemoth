package de.tfelix.bestia.ai.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This {@link StateTransformer} does only switch between all registered states
 * randomly. It should be used for testing purposes.
 * 
 * @author Thomas Felix
 *
 */
public class RandomStateTransformer extends StateTransformer {
	
	private final Random rand = ThreadLocalRandom.current();
	
	private final List<State> registeredStates = new ArrayList<>();
	
	public void registerState(State state) {
		registeredStates.add(Objects.requireNonNull(state));
	}

	@Override
	public State evaluateState(State currentState) {
		final int i = rand.nextInt(registeredStates.size());
		return registeredStates.get(i);
	}

	
}
