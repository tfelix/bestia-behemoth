
/*
 * The IndicatorResolvers job is to take the data model of a entity and find a suitable 
 * indicator for display which is then in turn requested from the IndicatorManager. 
 * To handle the click actions the Indicator itself will be used.
 * 
 * If the necessairy data of indicator options is not yet resolved it is the job of the
 * indicator resolver to ask the server which interaction options we have with the entity.
 * As soon as the server has responded the indication is then updated and the user notified via
 * an updated indicator.
 */
export default class IndicatorResolver {

    constructor() {

    }


}