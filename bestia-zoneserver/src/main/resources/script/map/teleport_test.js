
function onTouch(toucherId) {
	Bestia.setPosition(toucherId, 10, 10);
}

function main() {
	var eid = Bestia.createEntity(area(10, 10, 1, 1));
	Bestia.setVisual(eid, 'teleport');
	Bestia.setOnEnter(eid, 'onTouch');
}