
var aoe = Bestia.createSpellEntity(shape, 'fireSprite', 15000);

aoe.setOnTouch(function(entity){
	Bestia.Entity.playAnimation(aoe, 'explosion');
});

aoe.setInterval(1000, function(){
	Bestia.findCollidingEntities(aoe).forEach(function(entity){
		entity.takeDamage('fire', 100);
	});
});