/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
	Bestia.info("Bestia Behemoth startup script.");
	Bestia.info("engine: MYSCRIPT " + SNAME);
	Bestia.info("engine: MYTYPE " + STYPE);
	
	
	Bestia.spawnMob("blob", 12, 15);
	
	var eid = Bestia.createEntity(point(10, 10));
	Bestia.setInterval(eid, 'map/test_spawner:checkEntities', 5000)
}
