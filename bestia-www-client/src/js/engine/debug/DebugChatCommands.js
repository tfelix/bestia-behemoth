import Signal from '../../io/Signal';

/**
 * This class listens to incoming chat debug commands towards the engine 
 * and then executes them if a known command is received.
 */
export default class DebugChatCommands {

    /**
     *  
     * @param Phaser.Game game - Reference to a phaser game.
     */
    constructor(pubsub, game) {

        this._pubsub = pubsub;

        this._pubsub.subscribe(Signal.ENGINE_DEBUG_CMD, this._handleCommand, this);
    }

    /**
     * Handles the incoming user command.
     * @param {String} _ Name of the topic.
     * @param {String} cmd The command text.
     */
    _handleCommand(_, cmd) {

        // TODO Das hier noch implementieren.
        console.log(cmd);

    }

}