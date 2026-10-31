package dev.smallproject.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The PHP indexer MUST NOT execute inside CompletionContributor's read action.
 * At most one background process runs at a time, and cached results are served immediately.
 */
object SemIdeService {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        { task -> Thread(task, "small-ide-completion").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val completions = BackgroundCompletionCache(executor)

    internal fun findCli(projectRoot: File, pluginJar: File?): File? {
        val installed = listOf(File(projectRoot, "vendor/bin/sem-ide"), File(projectRoot, "bin/sem-ide"))
            .firstOrNull { it.isFile }
        if (installed != null) return installed
        val root = pluginJar?.parentFile?.parentFile ?: return null
        return File(root, "core/bin/sem-ide").takeIf { it.isFile }
    }

    fun complete(project: Project, editor: Editor, context: QueryContext): List<String> {
        val root = project.basePath?.let(::File) ?: return emptyList()
        val jar = runCatching { File(SemIdeService::class.java.protectionDomain.codeSource.location.toURI()) }
            .getOrNull()
        val cli = findCli(root, jar) ?: return emptyList()
        val key = root.absolutePath + "\u0000" + cli.absolutePath + "\u0000" + SemIdeClient.encodeRequest(context)
        val offset = editor.caretModel.offset
        val stamp = editor.document.modificationStamp
        return completions.complete(
            key,
            load = { SemIdeClient.complete(root, cli, context, timeoutMs = 5000) },
            onReady = {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed && !editor.isDisposed &&
                        editor.caretModel.offset == offset &&
                        editor.document.modificationStamp == stamp
                    ) {
                        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                    }
                }
            },
        )
    }
}
