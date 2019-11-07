package de.tfelix.bestia.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.tfelix.bestia.ai.config.GroupConfig;
import de.tfelix.bestia.ai.state.RandomStateTransformer;
import de.tfelix.bestia.ai.state.SimpleState;

public class General {
	private GameEngine engine;
	
	@BeforeEach
	public void setup() {
		engine = Mockito.mock(GameEngine.class);
	}
	
	@Test
	public void test() {
		
		// Some api tests.
		AIManager manager = new AIManager(engine);

		RandomStateTransformer transformer = new RandomStateTransformer();
		
		transformer.registerState(new SimpleState("idle"));
		transformer.registerState(new SimpleState("random_walk"));
		
		GroupConfig simpleGrpConfig = new GroupConfig("simple", transformer);
		
		// Add the group AI configs.
		manager.addGroupConfig(simpleGrpConfig);
		
		// Test Agend.
		Agent a = new Agent(12);
	}

}
