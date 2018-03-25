function onEffectExec() {
	Bestia.info('Hello from script.');
}

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

	Bestia.setCallbackOnce('onEffectExec', 1000)
	Bestia.setLivetime(entityId, 12000);
	Bestia.setInterval(entityId, 1000, 'onTouch');
}
