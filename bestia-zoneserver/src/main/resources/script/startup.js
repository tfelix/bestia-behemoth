/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
	BAPI.info("Bestia Behemoth startup script.");
	BAPI.info("engine: MYSCRIPT " + MYSCRIPT);
	BAPI.info("engine: MYTYPE " + MYTYPE);
	BAPI.spawnMob("blob")
}
