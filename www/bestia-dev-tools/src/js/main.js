import 'phaser';
import StandbyState from './states/StandbyState';
import { version } from '../../package.json';
import '../css/main.scss';

require('file-loader?name=[name].[ext]!../index.html');

console.log('Bestia Toolsuite - v' + version);

let config = {
    type: Phaser.WEBGL,
    width: 500,
    height: 500,
    parent: 'engine',
    scene: StandbyState
};

let game = new Phaser.Game(config);
game.scene.add('standby', standbyState, true);
