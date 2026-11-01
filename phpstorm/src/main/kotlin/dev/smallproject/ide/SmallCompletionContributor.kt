package dev.smallproject.ide

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager

/** Small ORM query completion. The native project scan never runs in the editor read action. */
class SmallCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        ProgressManager.checkCanceled()
        val project = parameters.position.project
        val document = PsiDocumentManager.getInstance(project).getDocument(parameters.originalFile) ?: return
        // CompletionParameters.position may contain a synthetic PSI dummy identifier.
        // The real editor caret offset belongs to the original document.
        val offset = parameters.editor.caretModel.offset.coerceIn(0, document.textLength)
        if (offset > QueryContextResolver.MAX_SOURCE_LENGTH) return

        val source = document.charsSequence.subSequence(0, offset).toString()
        val context = QueryContextResolver.resolve(source) ?: return
        val completion = result.withPrefixMatcher(context.typedPrefix)
        for (insertText in SemIdeService.complete(project, parameters.editor, context)) {
            ProgressManager.checkCanceled()
            completion.addElement(element(context, insertText))
        }
    }

    private fun element(context: QueryContext, insertText: String): LookupElementBuilder {
        val item = LookupElementBuilder.create(insertText)
        return when (context.kind) {
            "manager" -> item
                .withLookupString(insertText.substringAfterLast('\\').removeSuffix("::class"))
                .withTypeText("Small Manager", true)
            "condition-field", "condition-value" -> {
                val field = Regex("""getFieldForCondition\('(\w+)'(?:,\s*'(\w+)')?\)""")
                    .find(insertText)
                if (field == null) {
                    item.withTypeText("Condition value", true)
                } else {
                    val name = field.groupValues[1]
                    val alias = field.groupValues[2]
                    val label = if (alias.isBlank()) name else "$alias.$name"
                    item.withPresentableText(label)
                        .withLookupString(name)
                        .withLookupString(label)
                        .withTailText("  $insertText", true)
                        .withTypeText("ConditionField", true)
                }
            }
            "field", "alias", "relation", "projection", "parameter" ->
                item.withTypeText("Small ORM / " + context.kind, true)
                    .withInsertHandler { insertion, _ ->
                        val document = insertion.document
                        val edit = if (context.kind == "field") {
                            QualifiedFieldInsertion.plan(document.text, insertion.startOffset, insertion.tailOffset, insertText)
                                ?: PhpStringInsertion.plan(document.text, insertion.startOffset, insertion.tailOffset, insertText)
                        } else {
                            PhpStringInsertion.plan(document.text, insertion.startOffset, insertion.tailOffset, insertText)
                        }
                        if (edit != null) {
                            document.replaceString(edit.start, edit.end, edit.replacement)
                            insertion.editor.caretModel.moveToOffset(edit.caretOffset)
                        }
                    }
            "condition-operator" -> {
                val label = insertText.substringAfterLast('\\')
                item.withPresentableText(label)
                    .withLookupString(label)
                    .withLookupString(label.substringAfter("::"))
                    .withTypeText("ConditionOperatorType", true)
            }
            else -> item.withTypeText("Small ORM / " + context.kind, true)
        }
    }
}
