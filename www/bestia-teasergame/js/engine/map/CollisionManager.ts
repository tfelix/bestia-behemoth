import { EngineContext } from '../EngineContext';

export class CollisionManager {

  constructor(
    private readonly ctx: EngineContext
  ) {

    const grid = new Array(100);
    for (let i = 0; i < grid.length; i++) {
      const element = new Array(100);
      element.fill(0);
      grid[i] = element;
    }
    ctx.pathfinder.setGrid(grid);
    ctx.pathfinder.setAcceptableTiles(0);
  }

  public update() {
    this.ctx.pathfinder.calculate();
  }

  public updateGrid() {

  }
}
