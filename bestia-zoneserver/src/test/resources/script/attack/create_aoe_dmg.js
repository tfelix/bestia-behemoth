function onTouch() {
	Bestia.info('Hello from script.');
}

function bla() {
	Bestia.findCollidingEntities(aoe).forEach(function(entity) {
		entity.takeDamage('fire', 100);
	});
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
	
	Bestia.setLivetime(entityId, 12000);
	Bestia.setScript(entityId, MYSELF, MYTYPE);
	Bestia.setInterval(entityId, 1000, 'onTouch');
}
