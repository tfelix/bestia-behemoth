function onTick() {
	var self = Bestia.findEntity(SELF);



	/*entitiesInRange.forEach(e =>
	    // Attack.calculateDamage(e);
	    // Bestia.log(e.getPosition());
	);*/
}

var e = Bestia.findEntity(10);
Bestia.info(e.getPosition());

Bestia.newEntity()
    .setVisual('atk_ember')
    .setLivetime(10000)
    //.setShape(rect(0, 0, 0, 5, 5, 5))
    .setPosition(TARGET_POSITION)
    .copyStatusValuesFrom(OWNER)
    .scriptApi()
        .setInterval(1000, 'onTick');
