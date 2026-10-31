package dev.smallproject.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager

/** Literal-aware completion popup: enables Ctrl+Space for ORM fields and relations. */
class SmallCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val project = parameters.position.project
        val document = PsiDocumentManager.getInstance(project).getDocument(parameters.originalFile) ?: return
        val offset = parameters.offset.coerceIn(0, document.textLength)
        val precedingSource = document.charsSequence.subSequence(0, offset).toString()
        val context = QueryContextResolver.resolve(precedingSource) ?: return
        ProgressManager.checkCanceled()
        // The default matcher only considers the text after a dot, not the full PHP literal.
        val completion = result.withPrefixMatcher(context.typedPrefix)
        for (name in SemIdeService.complete(project, context)) {
            ProgressManager.checkCanceled()
            completion.addElement(
                LookupElementBuilder.create(name).withTypeText("Small ORM / " + context.kind, true)
            )
        }
    }
}
