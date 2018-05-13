/// <reference path="./phaser.d.ts"/>
import 'phaser';
import * as LOG from 'loglevel';

import { BootScene } from './scenes/BootScene';
import { GameScene } from './scenes/GameScene';

const config: GameConfig = {
  title: 'Bestia',
  url: 'https://bestia-game.net',
  version: '1.0',
  width: 800,
  height: 600,
  zoom: 1,
  type: Phaser.AUTO,
  parent: 'game',
  scene: [BootScene, GameScene],
  input: {
    keyboard: true,
    mouse: true,
    touch: false,
    gamepad: false
  },
  backgroundColor: '#8abbc1',
  pixelArt: true,
  antialias: false
};

LOG.setLevel('debug');

export class Game extends Phaser.Game {
  constructor(config: GameConfig) {
    super(config);
  }
}

window.onload = () => {
  const game = new Game(config);
};
