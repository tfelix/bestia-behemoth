/**
 DESC: Spawn a single attackable stone to the world.
 */
 
 api.entity()
	.setSprite('emperium')
	.setPosition(targetX, targetY)
	.setHp(25)
	.setTickCallback(360000, function(){ 
		kill(); })
	.spawn();