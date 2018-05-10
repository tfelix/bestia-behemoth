import { BestiaObject } from './BestiaObject';

class MultiSprite extends Phaser.GameObjects.Sprite implements BestiaObject {

  private jumpKey: Phaser.Input.Keyboard.Key;
  private anim: Phaser.Tweens.Tween[];
  private isDead: boolean = false;

  public getDead(): boolean {
    return this.isDead;
  }

  public setDead(dead): void {
    this.isDead = dead;
  }

  constructor(params) {
    super(params.scene, params.x, params.y, params.key, params.frame);


    params.scene.add.existing(this);
  }

  update(): void {

  }
}