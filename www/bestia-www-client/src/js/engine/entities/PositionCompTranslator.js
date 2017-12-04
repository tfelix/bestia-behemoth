import ComponentNames from './ComponentNames';

/**
 * The PositionComponentTranslator will transform a incoming component
 * into a usable data object.
 */
export default class PositionCompTranslator {
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
		return componentMsg.ct === ComponentNames.POSITION;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.POSITION,
			eid: componentMsg.eid,
			id: componentMsg.pl.id,
			position: {
				x: componentMsg.pl.p.x, 
				y: componentMsg.pl.p.y
			},
			shapeType: 'point'
		};
	}
}
