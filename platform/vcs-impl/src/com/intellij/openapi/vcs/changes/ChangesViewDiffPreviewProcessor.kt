// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.*
import com.intellij.openapi.vcs.changes.actions.diff.SelectionAwareGoToChangePopupActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffFromLocalChangesActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.util.ui.tree.TreeUtil
import one.util.streamex.StreamEx
import java.util.stream.Stream

private fun wrap(changes: Stream<Change>, unversioned: Stream<FilePath>): Stream<Wrapper> =
  Stream.concat(
    changes.map { ChangeWrapper(it) },
    unversioned.map { UnversionedFileWrapper(it) }
  )

private class ChangesViewDiffPreviewProcessor(private val changesView: ChangesListView,
                                              place : String) :
  ChangeViewDiffRequestProcessor(changesView.project, place) {

  init {
    putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
  }

  override fun getSelectedChanges(): Stream<Wrapper> =
    if (changesView.isSelectionEmpty) allChanges
    else wrap(StreamEx.of(changesView.selectedChanges.iterator()), StreamEx.of(changesView.selectedUnversionedFiles.iterator()))

  override fun getAllChanges(): Stream<Wrapper> = wrap(StreamEx.of(changesView.changes.iterator()), changesView.unversionedFiles)

  override fun selectChange(change: Wrapper) {
    changesView.findNodePathInTree(change.userObject)?.let { TreeUtil.selectPath(changesView, it, false) }
  }

  override fun createGoToChangeAction(): AnAction {
    return MyGoToChangePopupProvider().createGoToChangeAction()
  }

  private inner class MyGoToChangePopupProvider : SelectionAwareGoToChangePopupActionProvider() {
    override fun getActualProducers(): List<DiffRequestProducer> {
      val currentChanges = changesView.selectedChanges.toList()
      val currentUnversioned = changesView.selectedUnversionedFiles.toList()
      return ShowDiffFromLocalChangesActionProvider.collectRequestProducers(project, currentChanges, currentUnversioned, changesView).list
    }

    override fun selectFilePath(filePath: FilePath) {
      val curChange = currentChange
      if (curChange !is ChangeWrapper) {
        this@ChangesViewDiffPreviewProcessor.selectFilePath(filePath)
      }
      else {
        (changesView.getAllChangesFromSameChangelist(curChange.change) ?: changesView.getAllChangesFromSameAmendNode(curChange.change))
          ?.find { ChangesUtil.getFilePath(it) == filePath }
          ?.let { change ->
            val changeToSelect = ChangeWrapper(change)
            currentChange = changeToSelect
            selectChange(changeToSelect)
          }
        ?: this@ChangesViewDiffPreviewProcessor.selectFilePath(filePath)
      }
    }

    override fun getSelectedFilePath(): FilePath? {
      return this@ChangesViewDiffPreviewProcessor.selectedFilePath
    }
  }

  fun setAllowExcludeFromCommit(value: Boolean) {
    if (DiffUtil.isUserDataFlagSet(ALLOW_EXCLUDE_FROM_COMMIT, context) == value) return
    context.putUserData(ALLOW_EXCLUDE_FROM_COMMIT, value)
    fireDiffSettingsChanged()
  }

  fun fireDiffSettingsChanged() {
    dropCaches()
    updateRequest(true)
  }
}
