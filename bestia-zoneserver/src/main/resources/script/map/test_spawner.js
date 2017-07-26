

/**
 * Test Spawner script. Checks how many entities are alive and
 */

var SPAWN_COUNT = 10;

function checkEntities() {
	var entities = JSON.parse(BAPI.getScriptVar(SUID));
	var newEntities = [];

	var existingMobs = 0;

	// Hier passiert ein fehler
	entities.forEach(function(id) {
		if (BAPI.exists(id)) {
			existingMobs++;
			newEntities.push(id);
		}
	});
	
	for(var i = existingMobs; i < SPAWN_COUNT; i++) {
		spawnMob(newEntities);
	}
}

function spawnMob(entities) {
	var eid = BAPI.spawnMob("blob", 10, 10);
	entities.push(eid);
	BAPI.setScriptVar(SUID, JSON.stringify(entities));
}
