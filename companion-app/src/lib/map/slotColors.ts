/**
 * One colour per owned entity, the same as the game client's OwnedSlots, so a bestia's route is the same colour
 * on both maps: the master first, then each bestia in the order the roster lists them.
 */
export const MASTER_COLOR = '#ffdb59';

const BESTIA_COLORS = ['#ff7340', '#f259d9', '#99ff4d', '#9973ff', '#33d9b3', '#ffa6bf', '#f2f2f2', '#d9264d'];

/** The colour of the bestia at [index] in the roster. */
export function bestiaColor(index: number): string {
	return BESTIA_COLORS[index % BESTIA_COLORS.length];
}
