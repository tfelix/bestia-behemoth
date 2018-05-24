export class Point {
  constructor(
    public readonly x: number,
    public readonly y: number
  ) {
  }

  public getDistance(p: Point): number {
    const dx = p.x - this.x;
    const dy = p.y - this.y;
    return Math.sqrt(dx * dx + dy * dy);
  }
}
