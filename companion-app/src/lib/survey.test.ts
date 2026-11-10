import { describe, expect, it } from 'vitest';
import { arcPath, dialPoint, isInArc, needleAt, surveyRefusal, workLeft } from './survey';

describe('needleAt', () => {
	it('starts straight up, swings right first, then left', () => {
		expect(needleAt(0, 2000)).toBeCloseTo(0);
		expect(needleAt(500, 2000)).toBeCloseTo(90);
		expect(needleAt(1500, 2000)).toBeCloseTo(-90);
		expect(needleAt(2000, 2000)).toBeCloseTo(0);
	});
});

describe('isInArc', () => {
	it('counts the arc edges as inside', () => {
		expect(isInArc(-30, -40, 20)).toBe(true);
		expect(isInArc(-29, -40, 20)).toBe(false);
	});
});

describe('dialPoint', () => {
	it('puts 0 degrees above the pivot and +90 to its right', () => {
		const up = dialPoint(100, 100, 50, 0);
		const right = dialPoint(100, 100, 50, 90);
		expect(up.x).toBeCloseTo(100);
		expect(up.y).toBeCloseTo(50);
		expect(right.x).toBeCloseTo(150);
		expect(right.y).toBeCloseTo(100);
	});
});

describe('arcPath', () => {
	it('stays on the half circle near its ends', () => {
		expect(arcPath(100, 100, 50, 85, 20)).toBe('M 148.30 87.06 A 50 50 0 0 1 150.00 100.00');
	});
});

describe('surveyRefusal', () => {
	it('names each refusal the api-server answers with', () => {
		expect(surveyRefusal(403)).toContain('Cartography level');
		expect(surveyRefusal(422)).toContain('ink');
		expect(surveyRefusal(500)).toBeNull();
	});
});

describe('workLeft', () => {
	it('rounds up to whole minutes', () => {
		expect(workLeft(420)).toBe('7 min');
		expect(workLeft(421)).toBe('8 min');
		expect(workLeft(5)).toBe('1 min');
	});
});
