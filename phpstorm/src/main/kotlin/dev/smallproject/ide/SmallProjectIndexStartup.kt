package dev.smallproject.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Pre-index PHP source files when a project opens, without needing a local PHP runtime. */
class SmallProjectIndexStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        SemIdeService.warmUp(project)
    }
}
