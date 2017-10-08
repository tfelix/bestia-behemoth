import Authenticator from '../../src/js/io/Authenticator';
import MID from '../../src/js/io/messages/MID';
import AuthenticateMessage from '../../src/js/message/external/AuthenticateMessage';
import Pubsub from '../../src/js/util/PubSub';
import Storage from '../../src/js/util/Storage';
import Signal from '../../src/js/io/Signal';

var AUTH_DATA = {
    accountId: 123,
    token: 'test1234-1234-1234'
};

class MockStorage {

    constructor() {

    }

    getAuth() {
        return AUTH_DATA;
    }
}

describe('Authenticator', function () {

    this.auth = null;
    this.pubsub = null;
    this.stor = null;

    this.AUTH_DENY = {
        state: 'DENIED'
    };

    this.AUTH_ACCEPT = {
        state: 'ACCEPTED'
    };

    beforeEach(function () {

        this.pubsub = new Pubsub();
        this.stor = new Storage();

        this.auth = new Authenticator(this.pubsub, this.stor);
    });

    describe('ctor', function () {
        it('throws on null pubsub.', function () {
            (function () {
                new Authenticator(null, new Storage());
            }).should.throw;
        });

        it('throws on null storage.', function () {
            (function () {
                new Authenticator(new Pubsub(), null);
            }).should.throw;
        });

        it('works when pubsub is given.', function () {
            (function () {
                new Authenticator(new Pubsub(), new Storage());
            }).should.throw;
        });
    });

    it('sends connection request upon connection.', function (done) {

        this.pubsub.subscribe(Signal.IO_SEND_MESSAGE, function (_, msg) {

            msg.mid.should.be.equal(MID.SYSTEM_AUTH);
            msg.accId.should.be.equal(AUTH_DATA.accountId);
            msg.token.should.be.equal(AUTH_DATA.token);
            done();

        });
        this.pubsub.publish(Signal.IO_CONNECT);
    });

    it('signales if there was a working authentication.', function () {

        var wasAuthError = false;
        var wasDisconnected = false;
        var wasConnected = false;

        this.pubsub.subscribe(Signal.IO_AUTH_CONNECTED, function () {
            wasConnected = true;
        });

        this.pubsub.subscribe(Signal.IO_AUTH_ERROR, function () {
            wasAuthError = true;
        });

        this.pubsub.subscribe(Signal.IO_DISCONNECT, function () {
            wasDisconnected = true;
        });

        this.pubsub.publish(MID.SYSTEM_AUTHREPLY, this.AUTH_ACCEPT);
        
        wasAuthError.should.be.false;
        wasDisconnected.should.be.false;
        wasConnected.should.be.true;

    });

    it('signales an error and stops connection if there was an error.', function () {

        var wasAuthError = false;
        var wasDisconnected = false;
        var wasConnected = false;

        this.pubsub.subscribe(Signal.IO_AUTH_CONNECTED, function () {
            wasConnected = true;
        });

        this.pubsub.subscribe(Signal.IO_AUTH_ERROR, function () {
            wasAuthError = true;
        });

        this.pubsub.subscribe(Signal.IO_DISCONNECT, function () {
            wasDisconnected = true;
        });

        this.pubsub.publish(MID.SYSTEM_AUTHREPLY, this.AUTH_DENY);
        
        wasAuthError.should.be.true;
        wasDisconnected.should.be.true;
        wasConnected.should.be.false;
    });
});