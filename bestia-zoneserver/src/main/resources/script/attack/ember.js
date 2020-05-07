function onTick() {
	var self = Bestia.findEntity(SELF);

	/*entitiesInRange.forEach(e =>
	    // Attack.calculateDamage(e);
	    // Bestia.log(e.getPosition());
	);*/
}

function main() {
    Bestia.newEntity()
        .setVisual('atk_ember')
        .setLivetime(5000)
        //.setShape(rect(0, 0, 0, 5, 5, 5))
        .setPosition(TARGET_POSITION)
        .copyStatusValuesFrom(OWNER)
        .script()
        .setInterval(1000, 'onTick');
}
