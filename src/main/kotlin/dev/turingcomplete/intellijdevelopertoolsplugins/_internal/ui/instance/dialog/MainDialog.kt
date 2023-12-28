package dev.turingcomplete.intellijdevelopertoolsplugins._internal.ui.instance.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBSplitter
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.JBEmptyBorder
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.settings.DeveloperToolsDialogSettings
import dev.turingcomplete.intellijdevelopertoolsplugins._internal.ui.content.ContentPanelHandler
import javax.swing.Action
import javax.swing.JComponent
import kotlin.concurrent.withLock

/**
 * Note regarding modality:
 * It seems that, as of IntelliJ 2023.1 and at least on macOS, a non-modal
 * dialog is always shown in front of the IDE window. This can also be seen
 * in IntelliJ's "UI DSL Showcase" dialog.
 */
internal class MainDialog(project: Project?)
  : DialogWrapper(project, null, true, IdeModalityType.MODELESS, false),
    Place.Navigator {
  // -- Properties -------------------------------------------------------------------------------------------------- //

  private val contentPanelHandler = ContentPanelHandler(project, disposable, DeveloperToolsDialogSettings.instance)

  // -- Initialization ---------------------------------------------------------------------------------------------- //

  init {
    title = "Developer Tools"
    setSize(950, 705)
    isModal = DeveloperToolsDialogSettings.instance.dialogIsModal
    init()
  }

  // -- Exposed Methods --------------------------------------------------------------------------------------------- //

  override fun createCenterPanel(): JBSplitter {
    return JBSplitter(0.25f).apply {
      dividerWidth = DIVIDER_WIDTH

      firstComponent = contentPanelHandler.toolsMenuTree.createWrapperComponent()

      secondComponent = contentPanelHandler.contentPanel.apply {
        border = JBEmptyBorder(0, 0, 0, DIVIDER_WIDTH)
      }
    }
  }

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  override fun createActions() = emptyArray<Action>()

  override fun createSouthPanel(): JComponent? = null

  override fun getDimensionServiceKey(): String? = MainDialog::class.java.name

  override fun getPreferredFocusedComponent(): JComponent = contentPanelHandler.toolsMenuTree

  override fun doOKAction() {
    DeveloperToolsDialogSettings.instance.dialogLock.withLock {
      DeveloperToolsDialogSettings.instance.currentDialog.set(null)
      super.doOKAction()
    }
  }

  override fun doCancelAction() {
    DeveloperToolsDialogSettings.instance.dialogLock.withLock {
      DeveloperToolsDialogSettings.instance.currentDialog.set(null)
      super.doCancelAction()
    }
  }

  // -- Private Methods --------------------------------------------------------------------------------------------- //
  // -- Inner Type -------------------------------------------------------------------------------------------------- //
  // -- Companion Object -------------------------------------------------------------------------------------------- //

  companion object {

    const val DIVIDER_WIDTH = 4
  }
}