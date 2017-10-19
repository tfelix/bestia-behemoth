
import Indicator from './Indicator.js';
import Signal from '../../io/Signal.js';

/**
 * Basic indicator for visualization of the mouse pointer.
 * 
 * @class Bestia.Engine.Indicator
 */
export default class AttackCastIndicator extends Indicator {
	constructor(manager) {
		super(manager);

		// TODO Marker vorbereiten.
		this._marker = null;
		
		// Listen for activation signal.
		this.pubsub.subscribe(Signal.ENGINE_CAST_ITEM, this._onCastAttack.bind(this));
	}
	
	_onClick() {
		// pointer
		// TODO Cast the item on the given spot.
		alert("Attacke Gecasted");
		
		// Forfeit control.
		this._manager.showStandardIndicator();
	}

	_onCastAttack() {
		// _, attack
		// Asks to get activated.
		this._requestActive();

		// Prepare the needed dynamic cast indicator.
		
		// Aktivieren.
		this._requestActive();
	}
} 