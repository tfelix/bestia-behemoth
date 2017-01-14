/*global Phaser */

import Signal from '../io/Signal';

/**
 * This helper will subscribe itself to player bestia changes and buffers the
 * player bestia until it changes for convenience.
 */
export default class PlayerBestiaHelper {
	
	constructor(pubsub) {
		if(!pubsub) {
			throw 'Pubsub can not be null';
		}
		
		/**
		 * The current player bestia.
		 */
		this.playerBestia = null;
		
		pubsub.getRef(ReferenceName.PlayerBestia, bestia => this.playerBestia = bestia);
		
		// Subscribe for future changes.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._onBestiaSelected, this);
	}
	
	/**
	 * Callback if the bestia changed.
	 */
	_onBestiaSelected(_, bestia) {
		this.playerBestia = bestia;
	}
}
