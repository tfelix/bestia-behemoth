function onTick() {
	Bestia.info('Hello from script.');
	// Get all Bestias in Range of fire_pillar

	// Make fire Damage
}

Bestia.info('Script: main().');

var entityId = Bestia.createEntity();

Bestia.setLivetime(entityId, 12000);
Bestia.setInterval(entityId, 1000, 'onTick');
