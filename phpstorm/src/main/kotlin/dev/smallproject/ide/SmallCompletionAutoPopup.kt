package dev.smallproject.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Schedules completion through IntelliJ's designated auto-popup interception hook. */
class SmallCompletionAutoPopup : TypedHandlerDelegate() {
    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '(' && c != ',' && c != '\'' && c != '"') return Result.CONTINUE
        if (editor.isDisposed) return Result.CONTINUE
        val document = editor.document
        val offset = editor.caretModel.offset
        if (offset > QueryContextResolver.MAX_SOURCE_LENGTH || offset > document.textLength) {
            return Result.CONTINUE
        }
        val tail = document.charsSequence.subSequence(maxOf(0, offset - 1024), offset)
        if (!INTERESTING_METHOD.containsMatchIn(tail)) return Result.CONTINUE
        val context = QueryContextResolver.resolve(
            document.charsSequence.subSequence(0, offset).toString(),
        ) ?: return Result.CONTINUE
        if (context.kind !in COMPLETION_KINDS) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    companion object {
        private val INTERESTING_METHOD = Regex(
            """->\s*(?:firstCondition|andCondition|orCondition|xorCondition|getFieldForCondition|setParameter|addOrderBy|select|addSelect|join|leftJoin|rightJoin|innerJoin|get)\s*\(""",
        )
        private val COMPLETION_KINDS = setOf(
            "condition-field", "condition-operator", "condition-value",
            "field", "alias", "relation", "projection", "parameter", "manager",
        )
    }
}
