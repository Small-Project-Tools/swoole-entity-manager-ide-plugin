package dev.smallproject.ide

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import java.io.File

/**
 * Local-only diagnostics: no source code, secrets or entity data is transmitted.
 */
class SmallCompletionDiagnosticsAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        val context = editor?.let {
            val offset = it.caretModel.offset
            if (offset > QueryContextResolver.MAX_SOURCE_LENGTH || offset > it.document.textLength) null
            else QueryContextResolver.resolve(it.document.charsSequence.subSequence(0, offset).toString())
        }
        val root = project.basePath?.let(::File)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching {
                if (root == null) null else NativeProjectScanner.scan(root)
            }
            val model = result.getOrNull()
            val manager = context?.manager?.trimStart('\\')
            val suggestions = if (context == null || model == null) emptyList() else model.complete(context)
            val report = buildString {
                appendLine("Native index (no PHP runtime needed)")
                appendLine("Project directory: " + (if (root?.isDirectory == true) "available" else "unavailable"))
                appendLine("Managers indexed: " + (model?.managers?.size ?: 0))
                appendLine("Mapped entities: " + (model?.entities?.size ?: 0))
                appendLine("Context: " + (context?.kind ?: "unresolved"))
                appendLine("Manager resolved: " + (manager != null && model?.managers?.containsKey(manager) == true))
                appendLine("Root alias: " + (context?.rootAlias ?: "unresolved"))
                appendLine("Join aliases found: " + (context?.joins?.size ?: 0))
                appendLine("Candidate completions: " + suggestions.size)
                appendLine("Completion index preloaded: " + SemIdeService.isIndexed(project))
                append(SmallCompletionTelemetry.report())
                if (result.isFailure) appendLine("Index error: " + result.exceptionOrNull()?.javaClass?.simpleName)
                appendLine()
                append("If the context is unresolved, invoke basic completion inside a supported method argument.")
            }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) Messages.showInfoMessage(project, report, "Small Entity Manager Diagnostics")
            }
        }
    }
}
