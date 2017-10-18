import 'phaser';
import StandbyState from './states/StandbyState';
import { version } from '../../package.json';
require('file-loader?name=[name].[ext]!../index.html');
require('file-loader?name=[name].[ext]!../css/main.css');
require('file-loader?name=[name].[ext]!../css/spectre.min.css');

console.log('Bestia Toolsuite - v' + version);

let config = {
    type: Phaser.WEBGL,
    width: 800,
    height: 600,
    parent: 'engine',
    scene: StandbyState
};

let game = new Phaser.Game(config);
game.scene.add('standby', standbyState, true);
