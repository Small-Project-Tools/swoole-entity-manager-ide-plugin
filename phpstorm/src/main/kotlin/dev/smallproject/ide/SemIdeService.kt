package dev.smallproject.ide

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** The native indexer is exclusively run in the background, never inside the completion read action. */
object SemIdeService {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(32),
        { task -> Thread(task, "small-ide-native-index").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val completions = BackgroundCompletionCache(executor)
    private data class Snapshot(val indexedAt: Long, val model: NativeProjectIndex)
    private val snapshots = ConcurrentHashMap<String, Snapshot>()
    private const val INDEX_TTL_MS = 30_000L

    fun complete(project: Project, editor: Editor, context: QueryContext): List<String> {
        // Enum cases and already-known parameter names require no project scan.
        if (context.kind == "condition-operator" || context.kind == "parameter") {
            return NativeProjectIndex(emptyMap(), emptyMap()).complete(context)
        }
        val root = project.basePath?.let(::File) ?: return emptyList()
        // The time bucket expires completed suggestions even if the project is not otherwise edited.
        val key = root.absolutePath + "\u0000" + (System.currentTimeMillis() / INDEX_TTL_MS) +
            "\u0000" + SemIdeClient.encodeRequest(context)
        val offset = editor.caretModel.offset
        val stamp = editor.document.modificationStamp
        return completions.complete(
            key,
            load = {
                val cached = snapshots[root.absolutePath]
                val now = System.currentTimeMillis()
                val model = if (cached != null && now - cached.indexedAt < INDEX_TTL_MS) cached.model else {
                    NativeProjectScanner.scan(root).also {
                        snapshots[root.absolutePath] = Snapshot(System.currentTimeMillis(), it)
                    }
                }
                model.complete(context)
            },
            onReady = {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed && !editor.isDisposed &&
                        editor.caretModel.offset == offset &&
                        editor.document.modificationStamp == stamp
                    ) AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                }
            },
        )
    }
}
