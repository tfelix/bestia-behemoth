import log from 'loglevel';

// JSDoc definitions.
/**
 * @typedef {Object} LOG
 */

/**
 * This module sets up a simple logging framework and exports the object for
 * usage by bestia systems.
 */

let LOG = log.noConflict();

// Config it.
LOG.enableAll();

export default LOG;