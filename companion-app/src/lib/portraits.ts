/**
 * The hand-drawn bestia portraits, the game client's own files: `Game/Bestia/DB/<id>_<identifier>.png`, and a
 * placeholder for every species without one.
 */
const files = import.meta.glob('../../../bestia-client/src/Game/Bestia/**/*.png', {
	query: '?url',
	import: 'default',
	eager: true
}) as Record<string, string>;

const byId = new Map<number, string>();
let missing = '';
for (const [path, url] of Object.entries(files)) {
	const name = path.split('/').pop()!;
	const id = /^(\d+)_/.exec(name);
	if (id) byId.set(Number(id[1]), url);
	else if (name === 'missing_portrait.png') missing = url;
}

export function portraitOf(speciesId: number): string {
	return byId.get(speciesId) ?? missing;
}
