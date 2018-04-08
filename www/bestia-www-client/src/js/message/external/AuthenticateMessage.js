import MID from '../../io/messages/MID';

/**
 * Tries to authenticate the user with the server.
 */
export default class AuthenticateMessage {

   /**
    * 
    * @param {number} accId The account id.
    * @param {string} token Login token which was previously set on the server.
    */
    constructor(accId, token) {
        this.mid = MID.SYSTEM_AUTH;

        this.accId = accId;
        this.token = token;
    }
}