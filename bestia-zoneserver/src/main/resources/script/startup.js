/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
	BAPI.info("Bestia Behemoth startup script.");
	BAPI.info("engine: MYSCRIPT " + SNAME);
	BAPI.info("engine: MYTYPE " + STYPE);
	
	
	//BAPI.spawnMob("blob", 12, 15);
	
	//var eid = BAPI.createEntity(point(10, 10));
	//BAPI.setInterval(eid, 'map/test_spawner:checkEntities', 5000)
}
