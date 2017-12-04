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
			id: componentMsg.pl.id,
			originalStatusPoints: {
				strength: componentMsg.pl.osp.str,
				vitality: componentMsg.pl.osp.vit,
				intelligence: componentMsg.pl.osp.int,
				willpower: componentMsg.pl.osp.will,
				agility: componentMsg.pl.osp.agi,
				dexterity: componentMsg.pl.osp.dex,
				magicDefense: componentMsg.pl.osp.mdef,
				physicalDefense: componentMsg.pl.osp.def
			},
			statusPoints: {
				strength: componentMsg.pl.sp.str,
				vitality: componentMsg.pl.sp.vit,
				intelligence: componentMsg.pl.sp.int,
				willpower: componentMsg.pl.sp.will,
				agility: componentMsg.pl.sp.agi,
				dexterity: componentMsg.pl.sp.dex,
				magicDefense: componentMsg.pl.sp.mdef,
				physicalDefense: componentMsg.pl.sp.def
			},
			statusBasedValues: {
				walkspeed: componentMsg.pl.sbv.w.speed,
				manaRegen: componentMsg.pl.sbv.manar,
				healthRegen: componentMsg.pl.sbv.hpr
			},
			element: componentMsg.pl.e,
			originalElement: componentMsg.pl.oe,
			conditionValues: {
				currentHp: componentMsg.pl.cv.chp,
				maxHp: componentMsg.pl.cv.mhp,
				currentMana: componentMsg.pl.cv.cmana,
				maxMana: componentMsg.pl.cv.mmana
			}
		};
	}
}
