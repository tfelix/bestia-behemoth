import ComponentNames from './ComponentNames';

/**
 * The PositionComponentTranslator will transform a incoming component
 * into a usable data object.
 */
export default class VisualComponentTranslator {
	constructor() {
		// no op.
	}

	/**
	 * Checks if the translator can translate a incoming component.
	 * 
	 * @param {object} componentMsg Component message.
	 * @returns {boolean} TRUE if the componnet can be translated. FALSE otherwise.
	 */
	handlesComponent(componentMsg) {
		return componentMsg.ct === ComponentNames.VISIBLE;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.VISIBLE,
			eid: componentMsg.eid,
			id: componentMsg.pl.id,
			visible: componentMsg.pl.vis,
			visual: {
				sprite: componentMsg.pl.v.s,
				type: componentMsg.pl.v.t
			}
		};
	}
}
