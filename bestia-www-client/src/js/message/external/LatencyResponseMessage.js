
/**
 * This message reposts back the latency to the server.
 */
export default class LatencyResponseMessage {

    /**
     * Ctor.
     * @param latencyRequestMsg Message which states the request of the latency measurement.
     */
    constructor(latencyRequestMsg) {
        this.mid = LatencyResponseMessage.MID;

        // Kepp the timestamp.
        this.s = latencyRequestMsg.s;
    }

}

LatencyResponseMessage.MID = 'lat.res';