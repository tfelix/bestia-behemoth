import MID from '../io/messages/MID';
import LatencyResponseMessage from '../message/external/LatencyResponseMessage';


/**
 * The latency reporter reacty upon latenca measurement messages and basically 
 * just resend them back to the server as soon as possible. This will be used 
 * by the server to measure the latency roundtrip and to notify the player 
 * about message delay.
 */
export default class LatencyReporter {

    constructor(pubsub) {

        this.pubsub = pubsub;

        // Setup the binding.
        this.pubsub.subscribe(MID.LATENCY_REQ, this._handleRequest, this);
    }

    /**
     * Handle the latency request of the server. Basically turn the message 
     * into a latency response and resend it.
     */
    _handleRequest(_, msg) {

        var reply = new LatencyResponseMessage(msg);
        this.pubsub.send(reply);
    }
}