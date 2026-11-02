package dev.smallproject.ide

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

/**
 * PHP can suppress automatic completion when the first argument is a temporarily
 * incomplete, unquoted alias such as getFieldForCondition(owner.).
 * Opt out of that suppression only for a known Small ORM field alias.
 */
class SmallCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        if (offset < 1 || offset > QueryContextResolver.MAX_SOURCE_LENGTH) return ThreeState.UNSURE
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
            ?: return ThreeState.UNSURE
        if (offset > document.textLength) return ThreeState.UNSURE
        val sourceBeforeCaret = document.charsSequence.subSequence(0, offset).toString()
        if (!shouldForceSmallFieldPopup(sourceBeforeCaret)) return ThreeState.UNSURE
        SmallCompletionTelemetry.recordAutoPopupOverride()
        return ThreeState.NO
    }

    companion object {
        private val bareQualifiedField = Regex(
            """->\s*getFieldForCondition\s*\(\s*(?:fieldName\s*:\s*)?[A-Za-z_]\w*\.$""",
        )

        /** Pure guard for automated regression tests. Never widens unrelated PHP completion. */
        internal fun shouldForceSmallFieldPopup(sourceBeforeCaret: String): Boolean {
            if (sourceBeforeCaret.length > QueryContextResolver.MAX_SOURCE_LENGTH) return false
            if (!bareQualifiedField.containsMatchIn(sourceBeforeCaret)) return false
            val context = QueryContextResolver.resolve(sourceBeforeCaret) ?: return false
            if (context.kind != "field" || !context.typedPrefix.endsWith('.')) return false
            val alias = context.typedPrefix.substringBefore('.')
            return alias == context.rootAlias || context.joins.any {
                it["alias"] == alias || (it["alias"] == null && it["relation"] == alias)
            }
        }
    }
}
