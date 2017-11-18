import Point from './Point';

/**
 * Creates a point from the given json coordiante object.
 * 
 * @returns Point object created from JSON.
 */
export default (json) =>  {
    return new Point(json.x, json.y);
};
