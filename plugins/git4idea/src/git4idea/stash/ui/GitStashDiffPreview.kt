// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.actions.diff.SelectionAwareGoToChangePopupActionProvider
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.runInEdtAsync
import git4idea.stash.ui.GitStashUi.Companion.GIT_STASH_UI_PLACE
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asSequence

class GitStashDiffPreview(project: Project, private val tree: ChangesTree, parentDisposable: Disposable) :
  ChangeViewDiffRequestProcessor(project, GIT_STASH_UI_PLACE) {

  val toolbarWrapper get() = myToolbarWrapper

  init {
    tree.addSelectionListener(Runnable {
      updatePreviewLater(tree.isModelUpdateInProgress)
    }, this)

    Disposer.register(parentDisposable, this)

    updatePreviewLater(false)
  }

  private fun updatePreviewLater(modelUpdateInProgress: Boolean) {
    runInEdtAsync(this) { updatePreview(component.isShowing, modelUpdateInProgress) }
  }

  override fun getSelectedChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.selected(tree))
  }

  override fun getAllChanges(): Stream<Wrapper> {
    return wrap(VcsTreeModelData.all(tree))
  }

  override fun createGoToChangeAction(): AnAction {
    return MyGoToChangePopupProvider().createGoToChangeAction()
  }

  private inner class MyGoToChangePopupProvider : SelectionAwareGoToChangePopupActionProvider() {
    override fun getActualProducers(): List<DiffRequestProducer> {
      return allChanges.asSequence().mapNotNull { wrapper -> wrapper.createProducer(project) }.toList()
    }

    override fun selectFilePath(filePath: FilePath) {
      this@GitStashDiffPreview.selectFilePath(filePath)
    }

    override fun getSelectedFilePath(): FilePath? {
      return this@GitStashDiffPreview.selectedFilePath
    }
  }

  override fun selectChange(change: Wrapper) {
    val node = TreeUtil.findNode(tree.root, Condition { sameChange(change.userObject, it.userObject) }) ?: return
    TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean = false

  private fun wrap(treeModelData: VcsTreeModelData): Stream<Wrapper> {
    return treeModelData.userObjectsStream(Change::class.java).map { MyChangeWrapper(it) }
  }

  companion object {
    private fun sameChange(change1: Any, change2: Any): Boolean {
      if (change1 !is Change) return false
      if (change2 !is Change) return false
      return sameChange(change1, change2)
    }

    private fun sameChange(change1: Change, change2: Change): Boolean {
      if (change1.beforeRevision?.file != change2.beforeRevision?.file) return false
      if (change1.beforeRevision?.revisionNumber != change2.beforeRevision?.revisionNumber) return false
      if (change1.afterRevision?.file != change2.afterRevision?.file) return false
      if (change1.afterRevision?.revisionNumber != change2.afterRevision?.revisionNumber) return false
      return true
    }
  }

  private class MyChangeWrapper(change: Change) : ChangeWrapper(change) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as MyChangeWrapper

      return sameChange(change, other.change)
    }

    override fun hashCode(): Int {
      return Objects.hash(change.beforeRevision?.file, change.beforeRevision?.revisionNumber,
                          change.afterRevision?.file, change.afterRevision?.revisionNumber)
    }
  }
}
