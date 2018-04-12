package net.bestia.entity.component

import net.bestia.entity.component.condition.SyncCondition
import net.bestia.entity.component.condition.TrueCondition
import net.bestia.entity.component.receiver.SyncReceiver
import net.bestia.entity.component.transform.IdentityTransform
import net.bestia.entity.component.transform.SyncTransformer
import kotlin.reflect.KClass

/**
 * Annotated components will be synchronized with a client.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ClientDirective(
        val receiver: KClass<out SyncReceiver>,
        val transform: KClass<out SyncTransformer<*>> = IdentityTransform::class,
        val condition: KClass<out SyncCondition> = TrueCondition::class
)

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class ClientSync(
        val directives: Array<ClientDirective>
)