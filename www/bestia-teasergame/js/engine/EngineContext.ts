import * as EasyStar from 'easystarjs';
import { PlayerEntityHolder, EntityStore } from 'entities';

export class EngineContext {

  public mapGroup0: Phaser.GameObjects.Group;
  public pointerGroup: Phaser.GameObjects.Group;
  public pathfinder: EasyStar.js;

  constructor(
    public readonly game: Phaser.Scene,
    public readonly entityStore: EntityStore,
    public readonly playerHolder: PlayerEntityHolder
  ) {

    this.mapGroup0 = this.game.make.group({});

    // Easystar
    this.pathfinder = new EasyStar.js();
    this.pathfinder.enableDiagonals();
  }
}
