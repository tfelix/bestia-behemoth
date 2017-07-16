/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
	print("Bestia Behemoth startup script.");
	print("engine: MYSCRIPT " + MYSCRIPT);
	print("engine: MYTYPE " + MYTYPE);
	BAPI.spawnMob("blob")
}
