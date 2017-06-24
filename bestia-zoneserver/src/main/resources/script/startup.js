/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
	print("Das ist Test1");
	print("engine: MYSCRIPT " + MYSCRIPT);
	print("engine: MYTYPE " + MYTYPE);
	print("global: GLOB " + GLOB);
}
