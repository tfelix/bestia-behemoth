/**
 A small bonfire.
 */

var entity = {
data: [
	{type: 'particle', name: 'sparks', data: {count: 10, minScale: 0.1, maxScale: 0.3}},
	{type: 'sprite', name: 'bonfire_small'},
	{type: 'filter', name: 'heat'}
	],
template: [
	{ref: 'fire', name: 'bonfire_small', animation: 'burning'},
	{name: 'filter', target: 'fire', params: {size: 10}},
	{name: 'particle', target: 'fire', x: 10, y: 15}
	]
}

Bestia.Entities['bonfire_small'] = entity;