import ComponentNames from './ComponentNames';

export default class PlayerComponentTranslator {
	
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
		return componentMsg.ct === ComponentNames.PLAYER;
	}

	translate(componentMsg) {
		return {
			type: ComponentNames.PLAYER,
			eid: componentMsg.eid,
			id: 0,
			playerBestiaId: componentMsg.pl.pbid,
			databaseName: componentMsg.pl.dbn,
			customName: componentMsg.pl.cn
		};
	}
}
