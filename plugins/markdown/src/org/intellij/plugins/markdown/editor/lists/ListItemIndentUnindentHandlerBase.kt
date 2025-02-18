// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.refactoring.suggested.endOffset
import org.intellij.plugins.markdown.editor.lists.ListUtils.getListItemAtLine
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItemImpl

/**
 * This is a base class for classes, handling indenting/unindenting of list items.
 * It collects selected items (or the ones the carets are inside) and calls [doIndentUnindent] and [updateNumbering] on them one by one.
 */
internal abstract class ListItemIndentUnindentHandlerBase(private val baseHandler: EditorActionHandler?) : EditorWriteActionHandler.ForEachCaret() {

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
    baseHandler?.isEnabled(editor, caret, dataContext) == true

  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (caret == null || !doExecuteAction(editor, caret)) {
      baseHandler?.execute(editor, caret, dataContext)
    }
  }

  private fun doExecuteAction(editor: Editor, caret: Caret): Boolean {
    val file = PsiEditorUtil.getPsiFile(editor)
    val document = editor.document

    if (file !is MarkdownFile) return false

    val firstLinesOfSelectedItems = getFirstLinesOfSelectedItems(caret, document, file)

    // use lines instead of items, because items may become invalid before used
    for (line in firstLinesOfSelectedItems) {
      PsiDocumentManager.getInstance(file.project).commitDocument(document)
      val item = file.getListItemAtLine(line, document)!!

      if (!doIndentUnindent(item, file, document)) continue

      PsiDocumentManager.getInstance(file.project).commitDocument(document)
      run {
        @Suppress("name_shadowing") // item is not valid anymore, but line didn't change
        val item = file.getListItemAtLine(line, document)!!
        updateNumbering(item, file, document)
      }
    }
    return firstLinesOfSelectedItems.isNotEmpty()
  }

  private fun getFirstLinesOfSelectedItems(caret: Caret, document: Document, file: MarkdownFile): List<Int> {
    var line = document.getLineNumber(caret.selectionStart)
    val lastLine = if (caret.hasSelection()) document.getLineNumber(caret.selectionEnd - 1) else line

    val lines = mutableListOf<Int>()
    while (line <= lastLine) {
      val item = file.getListItemAtLine(line, document)
      if (item == null) {
        line++
        continue
      }

      lines.add(line)
      line = document.getLineNumber(item.endOffset) + 1
    }
    return lines
  }

  /** If this method returns `true`, then the document is committed and [updateNumbering] is called */
  protected abstract fun doIndentUnindent(item: MarkdownListItemImpl, file: MarkdownFile, document: Document): Boolean

  protected abstract fun updateNumbering(item: MarkdownListItemImpl, file: MarkdownFile, document: Document)
}
