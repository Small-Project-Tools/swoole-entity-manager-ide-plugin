package dev.smallproject.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Opens the completion dropdown when the user begins a supported Small query argument. */
class SmallCompletionAutoPopup : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '(' && c != ',' && c != '\'' && c != '"') return Result.CONTINUE
        if (editor.isDisposed) return Result.CONTINUE
        val document = editor.document
        val offset = editor.caretModel.offset
        if (offset > QueryContextResolver.MAX_SOURCE_LENGTH || offset > document.textLength) return Result.CONTINUE
        val tail = document.charsSequence.subSequence(maxOf(0, offset - 1024), offset)
        if (!INTERESTING_METHOD.containsMatchIn(tail)) return Result.CONTINUE
        val source = document.charsSequence.subSequence(0, offset).toString()
        val context = QueryContextResolver.resolve(source) ?: return Result.CONTINUE
        if (context.kind in COMPLETION_KINDS) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE
    }

    companion object {
        private val INTERESTING_METHOD = Regex(
            """->\s*(?:firstCondition|andCondition|orCondition|xorCondition|getFieldForCondition|setParameter|addOrderBy|join|leftJoin|rightJoin|innerJoin)\s*\(""",
        )
        private val COMPLETION_KINDS = setOf(
            "condition-field", "condition-operator", "condition-value",
            "field", "alias", "relation", "projection", "parameter",
        )
    }
}
