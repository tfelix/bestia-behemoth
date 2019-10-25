package de.tfelix.bestia.ai;

import de.tfelix.bestia.ai.action.Action;

/**
 * Reference which the game engine or server must implement so the AI library
 * can interact with it. It uses this main entry point to the engine in order to
 * perform certain tasks or actions the entities have to do inside the game
 * world context.
 * 
 * @author Thomas Felix
 *
 */
public interface GameEngine {

	/**
	 * Suggest the controlling game system a new tick rate of the game in order
	 * to evaluate the next AI state.
	 * 
	 * @param id
	 * @param tickRateMs
	 */
	void suggestTickRate(long id, int tickRateMs);

	/**
	 * The engine is asked to estimate a cost of a certain action. The unit of
	 * this action cost is not defined. It is used for the goal solver to search
	 * for an optimal solution path.
	 * 
	 * One good candidate might be the time it takes to complete an action. The
	 * cost only needs to be in a linear relationship between the different
	 * actions in order to make them comparable to each other.
	 * 
	 * @param action
	 *            The action to estimate the execution cost.
	 * @return The estimated costs of an action.
	 */
	int estimateActionCost(Action action);

}
