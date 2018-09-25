package net.bestia.zoneserver.script


/**
 * Methods of properties annotated with this will be made available
 * for editing via a PropertySetter.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD)
annotation class ScriptProperty(
    /**
     * This maps the value to this name. If the property
     * if of a basic type it will made accessible. If a
     * non basic type is annotated all its public member
     * are made available.
     */
    val value: String = "",
    /**
     * If the type of method could not be detected via its
     * name then the accesor is needed to specifiy it.
     * @return
     */
    val accessor: ScriptAccessor = ScriptAccessor.DEFAULT
)

enum class ScriptAccessor {
  GETTER, SETTER, DEFAULT
}
