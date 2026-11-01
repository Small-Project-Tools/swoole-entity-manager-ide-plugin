package dev.smallproject.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Schedules completion through IntelliJ's designated auto-popup interception hook. */
class SmallCompletionAutoPopup : TypedHandlerDelegate() {
    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result =
        if (scheduleIfRelevant(c, project, editor)) Result.STOP else Result.CONTINUE

    // Some PHP typed handlers consume '(' before checkAutoPopup can run.
    // charTyped is a fallback after the character has entered the document.
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        scheduleIfRelevant(c, project, editor)
        return Result.CONTINUE
    }

    private fun scheduleIfRelevant(c: Char, project: Project, editor: Editor): Boolean {
        if (c !in AUTO_CHARS || editor.isDisposed) return false
        val document = editor.document
        val offset = editor.caretModel.offset
        if (offset > QueryContextResolver.MAX_SOURCE_LENGTH || offset > document.textLength) return false
        val sourceBeforeCaret = document.charsSequence.subSequence(0, offset).toString()
        if (contextForTypedText(sourceBeforeCaret, c) == null) return false
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return true
    }

    companion object {
        private val AUTO_CHARS = setOf('(', ',', '\'', '"', '.')
        private val INTERESTING_METHOD = Regex(
            """->\s*(?:firstCondition|andCondition|orCondition|xorCondition|getFieldForCondition|setParameter|addOrderBy|select|addSelect|join|leftJoin|rightJoin|innerJoin|get)\s*\(""",
        )
        private val COMPLETION_KINDS = setOf(
            "condition-field", "condition-operator", "condition-value",
            "field", "alias", "relation", "projection", "parameter", "manager",
        )
        /** Accept the PSI/editor state both before and after typed-character insertion. */
        internal fun contextForTypedText(sourceBeforeCaret: String, c: Char): QueryContext? {
            if (c !in AUTO_CHARS || sourceBeforeCaret.length >= QueryContextResolver.MAX_SOURCE_LENGTH) {
                return null
            }
            val effectiveSource = if (sourceBeforeCaret.lastOrNull() == c) {
                sourceBeforeCaret
            } else sourceBeforeCaret + c
            if (!INTERESTING_METHOD.containsMatchIn(effectiveSource.takeLast(1024))) return null
            return QueryContextResolver.resolve(effectiveSource)?.takeIf {
                it.kind in COMPLETION_KINDS
            }
        }

    }
}
