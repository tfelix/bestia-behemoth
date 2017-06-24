function onTouch() {
	Bestia.info('Hello from script.');
}

print('Wird das gecalled?');

function main() {

	// Sprite
	// Animation
	// Duration
	// Position x
	// onEnter
	// onInterval
	// onLeave
	Bestia.info('Script: main().');
	
	var entityId = Bestia.createEntity();
	
	Bestia.setLivetime(entityId, 12000);
	// macht wenig sinn, da die meisten scripte 
	//Bestia.setScript(entityId, MYSELF, MYTYPE);
	Bestia.setInterval(entityId, 1000, 'onTouch');
}
