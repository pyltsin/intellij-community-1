/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.editor.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import org.jetbrains.jet.plugin.editor.KotlinSmartEnterHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.jet.lang.psi.JetWhileExpression
import org.jetbrains.jet.lang.psi.JetBlockExpression

public class KotlinMissingWhileBodyFixer: SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is JetWhileExpression) return
        val whileStatement = element as JetWhileExpression

        val doc = editor.getDocument()

        val body = whileStatement.getBody()
        if (body is JetBlockExpression) return
        if (body != null && body.startLine(doc) == whileStatement.startLine(doc) && whileStatement.getCondition() != null) return

        val rParenth = whileStatement.getRightParenthesis()
        if (rParenth == null) return

        doc.insertString(rParenth.range.end, "{}")
    }
}

