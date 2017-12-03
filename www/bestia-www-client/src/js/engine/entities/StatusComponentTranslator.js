import ComponentNames from './ComponentNames';

export default class StatusComponentTranslator {
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
		return componentMsg.ct === ComponentNames.STATUS;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.STATUS,
			eid: componentMsg.eid,
			id: componentMsg.c.id,
			originalStatusPoints: {
				strength: 0,
				vitality: 0,
				intelligence: 0,
				willpower: 0,
				agility: 0,
				dexterity: 0,
				magicDefense: 0,
				physicalDefense: 0
			},
			statusPoints: {
				strength: 0,
				vitality: 0,
				intelligence: 0,
				willpower: 0,
				agility: 0,
				dexterity: 0,
				magicDefense: 0,
				physicalDefense: 0
			},
			statusBasedValues: {

			},
			element: 'NORMAL',
			originalElement: 'NORMAL',
			conditionValues: {
				currentHp: 0,
				maxHp: 0,
				currentMana: 0,
				maxMana: 0
			}
		};
	}
}
