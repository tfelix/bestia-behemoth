function onTick() {
	// var entitiesInRange = Bestia.findEntities(shape);
	Bestia.info("geht")
	// entitiesInRange.forEach(e => Attack.calculateDamage(e));
}

function main() {
Bestia.newEntity()
    .setVisual('atk_ember')
    .setLivetime(10000)
    .setShape(rect(0, 0, 0, 5, 5, 5))
    .setPosition(TARGET_POSITION)
    .setStatusValuesFrom(OWNER)
    .scriptApi()
        .setInterval(1000, 'onTick');
}
