package dev.smallproject.ide

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Native completion with an eager, cancellable first index and a memory snapshot.
 *
 * IntelliJ already invokes CompletionContributor in a background completion thread.
 * Deferring the first result into a separate task returned no lookup items and made
 * the IDE fall back to unrelated global PHP suggestions.
 */
object SemIdeService {
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { task -> Thread(task, "small-ide-native-index").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val indexes = NativeIndexSnapshotCache(executor)

    fun complete(project: Project, editor: Editor, context: QueryContext): List<String> {
        if (context.kind == "condition-operator" || context.kind == "parameter") {
            return NativeProjectIndex(emptyMap(), emptyMap()).complete(context)
        }
        val root = project.basePath?.let(::File) ?: return emptyList()
        val model = indexes.get(root, ProgressManager::checkCanceled)
        return model.complete(context)
    }

    fun warmUp(project: Project) {
        val root = project.basePath?.let(::File) ?: return
        if (!project.isDisposed) indexes.warmUp(root)
    }

    fun isIndexed(project: Project): Boolean {
        val root = project.basePath?.let(::File) ?: return false
        return indexes.isIndexed(root)
    }
}
