import ComponentNames from './ComponentNames';

/**
 * The LevelComponentTranslator will transform a incoming component
 * into a usable data object.
 */
export default class LevelComponentTranslator {
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
		return componentMsg.ct === ComponentNames.LEVEL;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.LEVEL,
			eid: componentMsg.eid,
			id: componentMsg.pl.id,
			level: componentMsg.pl.lv,
			exp: componentMsg.pl.e
		};
	}
}