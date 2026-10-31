package dev.smallproject.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager

/** Adds Small ORM literal suggestions without blocking the editor on project indexing. */
class SmallCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        ProgressManager.checkCanceled()
        val project = parameters.position.project
        val document = PsiDocumentManager.getInstance(project).getDocument(parameters.originalFile) ?: return
        val offset = parameters.offset.coerceIn(0, document.textLength)
        // Do not allocate or parse an unbounded PHP file during an IDE read action.
        if (offset > QueryContextResolver.MAX_SOURCE_LENGTH) return
        val prefix = document.charsSequence.subSequence(0, offset).toString()
        val context = QueryContextResolver.resolve(prefix) ?: return
        ProgressManager.checkCanceled()
        val completion = result.withPrefixMatcher(context.typedPrefix)
        for (name in SemIdeService.complete(project, parameters.editor, context)) {
            ProgressManager.checkCanceled()
            completion.addElement(LookupElementBuilder.create(name).withTypeText("Small ORM / " + context.kind, true))
        }
    }
}
