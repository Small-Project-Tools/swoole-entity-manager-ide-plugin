package dev.smallproject.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager

class SmallCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val project = parameters.position.project
        val document = PsiDocumentManager.getInstance(project).getDocument(parameters.originalFile) ?: return
        val offset = parameters.offset.coerceIn(0, document.textLength)
        val prefix = document.charsSequence.subSequence(0, offset).toString()
        val context = QueryContextResolver.resolve(prefix) ?: return
        ProgressManager.checkCanceled()
        for (candidate in SemIdeService.complete(project, context)) {
            ProgressManager.checkCanceled()
            result.addElement(LookupElementBuilder.create(candidate).withTypeText("Small ORM", true))
        }
    }
}
