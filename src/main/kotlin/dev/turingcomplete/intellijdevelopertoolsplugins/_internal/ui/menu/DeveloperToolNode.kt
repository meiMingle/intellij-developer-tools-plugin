package dev.turingcomplete.intellijdevelopertoolsplugins._internal.ui.menu

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperTool
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperToolConfiguration
import dev.turingcomplete.intellijdevelopertoolsplugins.DeveloperToolPresentation
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.settings.DeveloperToolsInstanceSettings

internal class DeveloperToolNode(
  private val developerToolId: String,
  val parentDisposable: Disposable,
  val project: Project?,
  val settings: DeveloperToolsInstanceSettings,
  val developerToolPresentation: DeveloperToolPresentation,
  private val developerToolCreator: (DeveloperToolConfiguration) -> DeveloperTool
) : ContentNode(
  id = developerToolId,
  title = developerToolPresentation.menuTitle,
  toolTipText = developerToolPresentation.contentTitle
) {
  // -- Properties -------------------------------------------------------------------------------------------------- //

  private val _developerTools: MutableList<DeveloperToolContainer> by lazy {
    restoreDeveloperToolInstances().ifEmpty { listOf(doCreateNewDeveloperToolInstance()) }.toMutableList()
  }
  val developerTools: List<DeveloperToolContainer>
    get() = _developerTools

  // -- Initialization ---------------------------------------------------------------------------------------------- //
  // -- Exposed Methods --------------------------------------------------------------------------------------------- //

  fun createNewDeveloperToolInstance(): DeveloperToolContainer {
    val developerToolContainer = doCreateNewDeveloperToolInstance()
    _developerTools.add(developerToolContainer)
    return developerToolContainer
  }

  fun destroyDeveloperToolInstance(developerTool: DeveloperTool) {
    _developerTools.find { it.instance === developerTool }?.let {
      settings.removeDeveloperToolConfiguration(
        developerToolId = developerToolId,
        developerToolConfiguration = it.configuration
      )
      Disposer.dispose(developerTool)
    }
  }

  // -- Private Methods --------------------------------------------------------------------------------------------- //

  private fun restoreDeveloperToolInstances(): List<DeveloperToolContainer> =
    settings.getDeveloperToolConfigurations(developerToolId)
      .map { developerToolConfiguration -> doCreateNewDeveloperToolInstance(developerToolConfiguration) }

  private fun doCreateNewDeveloperToolInstance(
    developerToolConfiguration: DeveloperToolConfiguration = settings.createDeveloperToolConfiguration(developerToolId)
  ): DeveloperToolContainer {
    val developerTool = developerToolCreator(developerToolConfiguration)
    Disposer.register(parentDisposable, developerTool)
    return DeveloperToolContainer(developerTool, developerToolConfiguration, developerToolPresentation)
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  data class DeveloperToolContainer(
    val instance: DeveloperTool,
    val configuration: DeveloperToolConfiguration,
    val context: DeveloperToolPresentation
  )

  // -- Companion Object -------------------------------------------------------------------------------------------- //
}