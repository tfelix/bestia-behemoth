import MID from '../../io/messages/MID';
import LOG from '../../util/Log';

/**
 * The animation manager is responsible for fetching, caching and applying
 * animation procedures to entities objects. All animations must be stopped at
 * all time. Animations are a whole spectrum: they can be only animations
 * defined inside a sprite. But they can also be more complex composition as
 * animation chains defined in json files which must be aquired from the server
 * first.
 */
export default class AnimationManager {

	constructor(ctx) {

		this._pubsub = ctx.pubsub;

		this._curAnimId = 1;

		this._game = ctx.game;
		this._entityCache = ctx.entityCache;

		this._ctx = ctx;

		// Subscribe to the animation messages.
		this._pubsub.subscribe(MID.ANIMATION_PLAY, this._playAnimationHandler, this);
	}

	/**
	 * Handles the incoming play animation messages. It first checks if all
	 * ressources needed for the animation have been loaded and does so if this
	 * is not the case.
	 */
	_playAnimationHandler(_, msg) {
		LOG.debug('Playing animation message.', msg);
		
		// Check if its only a sprite based animation.
		if(this._isSpriteBased(msg)) {
			this._playSpriteBased(msg);
			return;
		}

		// Is the animation completly cached?
		// --> find the target of the animation and apply the transform to it.

		// Is the animation currently loading?
		// --> Attach it to the complete event.
		// --> if not load it.

	}
	
	/**
	 * Checks if the animation is sprite based.
	 */
	_isSpriteBased(msg) {
		return msg.eid !== 0 && 
			msg.an !== undefined && 
			msg.teid === 0 && 
			msg.op === null && 
			msg.tp === null;
	}
	
	/**
	 * Plays the sprite based animation.
	 */
	_playSpriteBased(msg) {
		let entity = this._entityCache.getEntity(msg.eid);
		
		if(entity === null) {
			LOG.error('Entity with id not found: ' + msg.eid);
			return;
		}
		
		entity.playAnimation(msg.an);
	}

	create() {

	}

	update() {

	}

	/**
	 * This start/adds the animation effects for the given entity. It will
	 * return an ID under which the running animation is referenced. It can be
	 * used to stop/remove the animation again from the entity. Note that some
	 * animations only run for a certain period of time and therefore remove
	 * themselves.
	 */
	play(entity, animation) {

		// do we have loaded the animation?

	}

	/**
	 * Stops and removes the given animation id from the entity. It will return
	 * TRUE if the animation was running and was stopped or FALSE if it was
	 * already stopped.
	 */
	stop(animId) {

	}
}
