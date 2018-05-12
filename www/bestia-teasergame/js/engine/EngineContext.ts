
export class EngineContext {

  public mapGroup0: Phaser.GameObjects.Group;
  public pointerGroup: Phaser.GameObjects.Group;

  constructor(
    public readonly game: Phaser.Scene
  ) {

    this.mapGroup0 = this.game.make.group({});
  }
}
