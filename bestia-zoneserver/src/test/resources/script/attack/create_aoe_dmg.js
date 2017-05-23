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
	var aoe = Bestia.createEntity();
	aoe.setLivetime(12000);
	aoe.setInterval(1000, 'onTouch');
}
