package dev.turingcomplete.intellijdevelopertoolsplugins

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperToolConfiguration.PropertyType.CONFIGURATION
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperToolConfiguration.PropertyType.INPUT
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperToolConfiguration.PropertyType.SECRET
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.common.uncheckedCastTo
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.settings.DeveloperToolsApplicationSettings
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.settings.DeveloperToolsInstanceSettings.Companion.assertPersistableType
import dev.turingcomplete.intellijdevelopertoolsplugins.common.ValueProperty
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DeveloperToolConfiguration(
  var name: String,
  val id: UUID = UUID.randomUUID(),
  val persistentProperties: Map<String, Any> = emptyMap()
) {
  // -- Properties -------------------------------------------------------------------------------------------------- //

  internal val properties = ConcurrentHashMap<String, PropertyContainer>()
  private val changeListeners = CopyOnWriteArrayList<ChangeListener>()
  var isResetting = false
    internal set

  // -- Initialization ---------------------------------------------------------------------------------------------- //
  // -- Exposed Methods --------------------------------------------------------------------------------------------- //

  fun <T : Any> register(
    key: String,
    defaultValue: T,
    propertyType: PropertyType = CONFIGURATION,
    example: T? = null
  ): ValueProperty<T> =
    properties[key]?.let { reuseExistingProperty(it) } ?: createNewProperty(defaultValue, propertyType, key, example)

  fun addChangeListener(parentDisposable: Disposable, changeListener: ChangeListener) {
    changeListeners.add(changeListener)
    Disposer.register(parentDisposable) { changeListeners.remove(changeListener) }
  }

  fun removeChangeListener(changeListener: ChangeListener) {
    changeListeners.remove(changeListener)
  }

  fun reset(
    type: PropertyType? = null,
    loadExamples: Boolean = DeveloperToolsApplicationSettings.instance.loadExamples
  ) {
    isResetting = true
    try {
      properties.filter { type == null || it.value.type == type }
        .forEach { (_, property) ->
          property.reset(loadExamples)
          fireConfigurationChanged(property.reference)
        }
    }
    finally {
      isResetting = false
    }
  }

  // -- Private Methods --------------------------------------------------------------------------------------------- //

  private fun <T : Any> reuseExistingProperty(property: PropertyContainer): ValueProperty<T> {
    if ((property.type == INPUT && !DeveloperToolsApplicationSettings.instance.saveInputs)
      || (property.type == CONFIGURATION && !DeveloperToolsApplicationSettings.instance.saveConfigurations)
      || (property.type == SECRET && !DeveloperToolsApplicationSettings.instance.saveSecrets)
    ) {
      property.reset(DeveloperToolsApplicationSettings.instance.loadExamples)
    }

    @Suppress("UNCHECKED_CAST")
    return property.reference as ValueProperty<T>
  }

  private fun <T : Any> createNewProperty(
    defaultValue: T,
    propertyType: PropertyType,
    key: String,
    example: T?
  ): ValueProperty<T> {
    val type = assertPersistableType(defaultValue::class, propertyType)
    val existingProperty = persistentProperties[key]
    val initialValue: T = existingProperty?.uncheckedCastTo(type) ?: let {
      if (DeveloperToolsApplicationSettings.instance.loadExamples && example != null) example else defaultValue
    }
    val valueProperty = ValueProperty(initialValue).apply {
      afterChangeConsumeEvent(null, handlePropertyChange(key))
    }
    properties[key] = PropertyContainer(
      key = key,
      reference = valueProperty,
      defaultValue = defaultValue,
      example = example,
      type = propertyType
    )
    return valueProperty
  }

  private fun fireConfigurationChanged(property: ValueProperty<out Any>) {
    changeListeners.forEach { it.configurationChanged(property) }
  }

  private fun <T : Any?> handlePropertyChange(key: String): (ValueProperty.ChangeEvent<T>) -> Unit = { event ->
    val newValue = event.newValue
    if (event.oldValue != newValue) {
      properties[key]?.let { property ->
        fireConfigurationChanged(property.reference)
      } ?: error("Unknown property: $key")
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  internal data class PropertyContainer(
    val key: String,
    val reference: ValueProperty<out Any>,
    val defaultValue: Any,
    val example: Any?,
    val type: PropertyType
  ) {

    fun reset(loadExamples: Boolean) {
      val value = if (example != null && loadExamples) example else defaultValue
      reference.setWithUncheckedCast(value)
    }

    fun valueChanged(): Boolean {
      val value = reference.get()
      return defaultValue != value && example != value
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  enum class PropertyType {

    CONFIGURATION,
    INPUT,
    SECRET,
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  interface ChangeListener {

    fun configurationChanged(property: ValueProperty<out Any>)
  }

  // -- Companion Object -------------------------------------------------------------------------------------------- //
}