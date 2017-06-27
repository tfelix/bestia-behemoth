/**
 * This is a test script. Upon walking into this script it will invoke a 
 * chat message to the player and write some text.
 * 
 * @param toucherId
 * @returns
 */

function onTouch(toucherId) {
	Bestia.setPosition(toucherId, 10, 10);
}

function main() {
	var eid = BAPI.createEntity(area(10, 10, 1, 1));
	BAPI.setVisual(eid, 'teleport');
	BAPI.setOnEnter(eid, 'onTouch');
}