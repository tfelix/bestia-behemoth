
var aoe = Bestia.createSpellEntity(null, 'fireSprite', 15000);

function onTouch(entity) {
	Bestia.info('Hello from script.');
}

aoe.setOnTouch(function(entity){
	Bestia.Entity.playAnimation(aoe, 'explosion');
});

aoe.setInterval(1000, 'onTouch');

function bla(){
	Bestia.findCollidingEntities(aoe).forEach(function(entity){
		entity.takeDamage('fire', 100);
	});
}