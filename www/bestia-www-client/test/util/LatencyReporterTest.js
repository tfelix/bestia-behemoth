import LatencyReporter from '../../src/js/util/LatencyReporter';
import LatencyResponseMessage from '../../src/js/message/external/LatencyResponseMessage';
import PubSub from '../../src/js/util/PubSub';
import MID from '../../src/js/io/messages/MID';
import Signal from '../../src/js/io/Signal';

describe('LatencyReporter', function () {

    this.pubsub = null;
    this.latency = null;

    beforeEach(function () {

        this.pubsub = new Pubsub();
        this.latency = new LatencyReporter(this.pubsub);
    });

    describe('#ctor', function () {
        it('throws with empty pubsub', function () {
            (function () {
                new LatencyReporter(null);
            }).should.throw;
        });

        it('works with all args.', function () {
            new LatencyReporter(this.pubsub);
        });
    })

    it('sends a LatencyResponseMessage upon server request.', function (done) {

        var reqMsg = {
            mid: MID.LATENCY_REQ,
            s: 123456
        }

        this.pubsub.subscribe(Signal.IO_SEND_MESSAGE, function(_, msg){
            msg.mid.shoud.be.equal(LatencyResponseMessage.MID);
            msg.s.should.be.equal(reqMsg.s);
            done();
        });

        this.pubsub.publish(reqMsg.mid, reqMsg);
    });
});