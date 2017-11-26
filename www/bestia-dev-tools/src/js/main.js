import 'phaser';
import * as ko from 'knockout';
import StandbyState from './states/StandbyState';
import { version } from '../../package.json';
import '../css/main.scss';
import DropController from './controller/DropController';

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

ko.applyBindings({
	dropController: new DropController()
});

