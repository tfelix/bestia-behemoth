
/**
 * Internal state of an agents must be represented in some way. Since this might
 * be dependent upon which kind of AI should be used we don't want to make hard
 * wired assumption how to save a internal agent state.
 * 
 * Components inside this package are used to either represent some kind of
 * internal state as well as a state manager which is able to transform this
 * state into a usable state for the engine.
 * 
 * @author Thomas Felix
 *
 */
package de.tfelix.bestia.ai.state;