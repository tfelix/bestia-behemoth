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
				strength: componentMsg.c.osp.str,
				vitality: componentMsg.c.osp.vit,
				intelligence: componentMsg.c.osp.int,
				willpower: componentMsg.c.osp.will,
				agility: componentMsg.c.osp.agi,
				dexterity: componentMsg.c.osp.dex,
				magicDefense: componentMsg.c.osp.mdef,
				physicalDefense: componentMsg.c.osp.def
			},
			statusPoints: {
				strength: componentMsg.c.sp.str,
				vitality: componentMsg.c.sp.vit,
				intelligence: componentMsg.c.sp.int,
				willpower: componentMsg.c.sp.will,
				agility: componentMsg.c.sp.agi,
				dexterity: componentMsg.c.sp.dex,
				magicDefense: componentMsg.c.sp.mdef,
				physicalDefense: componentMsg.c.sp.def
			},
			statusBasedValues: {
				walkspeed: componentMsg.c.sbv.w.speed,
				manaRegen: componentMsg.c.sbv.manar,
				healthRegen: componentMsg.c.sbv.hpr
			},
			element: componentMsg.c.e,
			originalElement: componentMsg.c.oe,
			conditionValues: {
				currentHp: componentMsg.c.cv.chp,
				maxHp: componentMsg.c.cv.mhp,
				currentMana: componentMsg.c.cv.cmana,
				maxMana: componentMsg.c.cv.mmana
			}
		};
	}
}
