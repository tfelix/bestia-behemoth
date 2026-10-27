/**
 * Short map references like `H12 41 37`; a copy of the server's `GridRef.kt`, held to the same cases in
 * `shared/src/test/resources/gridref-cases.tsv`. Squares are 8 km, named by a column letter from the west and a
 * row number from the south; inside one, equal halves of digits east then north, more digits for a finer cell.
 */
export const SQUARE_METRES = 8000;
export const MAX_DIGITS = 4;

export type GridPoint = { eastMetres: number; northMetres: number; precisionMetres: number };

const PATTERN = /^([A-Za-z]+)\s*(\d+)(?:\s+(\d+)(?:\s+(\d+))?)?$/;

/** Spreadsheet-style: 0 is A, 25 is Z, 26 is AA. */
export function columnName(index: number): string {
	let n = index + 1;
	let name = '';
	while (n > 0) {
		const rest = (n - 1) % 26;
		name = String.fromCharCode(65 + rest) + name;
		n = Math.floor((n - 1) / 26);
	}
	return name;
}

export function columnIndex(name: string): number {
	let n = 0;
	for (const c of name) n = n * 26 + (c.charCodeAt(0) - 64);
	return n - 1;
}

function cellMetres(digits: number): number {
	return 10 ** (MAX_DIGITS - digits);
}

function wrap(value: number, size: number): number {
	const wrapped = value % size;
	return wrapped < 0 ? wrapped + size : wrapped;
}

/** [digits] per half, 1 (1 km) to 4 (1 m). The world wraps, so any coordinate has a reference. */
export function formatGridRef(
	eastMetres: number,
	northMetres: number,
	worldWidth: number,
	worldHeight: number,
	digits = 2
): string {
	const east = wrap(eastMetres, worldWidth);
	const north = wrap(northMetres, worldHeight);
	const column = Math.floor(east / SQUARE_METRES);
	const row = Math.floor(north / SQUARE_METRES);

	const cell = cellMetres(digits);
	const inEast = Math.floor((east - column * SQUARE_METRES) / cell);
	const inNorth = Math.floor((north - row * SQUARE_METRES) / cell);

	return `${columnName(column)}${row + 1} ${String(inEast).padStart(digits, '0')} ${String(inNorth).padStart(digits, '0')}`;
}

/** Null when [text] is not a reference, or names a square or cell outside the world. */
export function parseGridRef(text: string, worldWidth: number, worldHeight: number): GridPoint | null {
	const match = PATTERN.exec(text.trim());
	if (!match) return null;
	const [, letters, rowText, first = '', second = ''] = match;

	const columns = Math.ceil(worldWidth / SQUARE_METRES);
	const rows = Math.ceil(worldHeight / SQUARE_METRES);
	const column = columnIndex(letters.toUpperCase());
	const row = Number(rowText) - 1;
	if (column >= columns || row < 0 || row >= rows) return null;

	if (first === '') {
		return {
			eastMetres: Math.min(column * SQUARE_METRES + SQUARE_METRES / 2, worldWidth),
			northMetres: Math.min(row * SQUARE_METRES + SQUARE_METRES / 2, worldHeight),
			precisionMetres: SQUARE_METRES
		};
	}

	let eastDigits = first;
	let northDigits = second;
	if (second === '') {
		if (first.length % 2 !== 0) return null;
		eastDigits = first.slice(0, first.length / 2);
		northDigits = first.slice(first.length / 2);
	}
	if (eastDigits.length !== northDigits.length || eastDigits.length > MAX_DIGITS) return null;

	const cell = cellMetres(eastDigits.length);
	const inEast = Number(eastDigits) * cell;
	const inNorth = Number(northDigits) * cell;
	if (inEast >= SQUARE_METRES || inNorth >= SQUARE_METRES) return null;

	const east = column * SQUARE_METRES + inEast;
	const north = row * SQUARE_METRES + inNorth;
	if (east >= worldWidth || north >= worldHeight) return null;

	return { eastMetres: east + cell / 2, northMetres: north + cell / 2, precisionMetres: cell };
}
