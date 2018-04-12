package net.bestia.entity.component


/**
 * Added to a component this annotation will automatically start a Component
 * managing actor upon installing this component and automatically remove the
 * actor if the component is deleted from the entity.
 *
 * @author Thomas Felix
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ActorSync(
        /**
         * Fully qualified actor name to get spawned if this component is
         * installed.
         */
        val value: String,
        /**
         * This flag determines if an instanced actor will actively updates
         * that an component has changed via a ComponentChangedMessage which holds
         * the component id which has changed.
         */
        val updateActorOnChange: Boolean = true
)
