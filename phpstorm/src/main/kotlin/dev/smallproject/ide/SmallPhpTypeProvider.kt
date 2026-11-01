package dev.smallproject.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

/**
 * Native PhpStorm inference for a Small manager factory and its query builders.
 * This provider uses only local PSI while getType is called by the IDE indexer.
 */
class SmallPhpTypeProvider : PhpTypeProvider4 {
    // Reserved for this plugin; do not reuse keys from PhpTypeSignatureKey.
    override fun getKey(): Char = 'Ϟ'

    override fun getType(element: PsiElement): PhpType? {
        val reference = element as? MethodReference ?: return null
        val name = reference.name ?: return null
        // Other Small methods have native PHP return type declarations.
        // Claiming them by a file-wide heuristic polluted unrelated method inference.
        if (name != "get") return null
        val file = element.containingFile ?: return null
        val source = file.text
        val offset = element.textOffset
        val argument = reference.parameters.firstOrNull()?.text ?: return null
        val receiver = reference.classReference?.text ?: return null
        val fqcn = SemTypeInference.factoryResult(source, offset, argument, receiver) ?: return null
        return PhpType().add(fqcn)
    }

    override fun complete(expression: String, project: Project): PhpType? = null

    override fun getBySignature(
        expression: String,
        visited: MutableSet<String>,
        depth: Int,
        project: Project,
    ): Collection<PhpNamedElement>? = null
}
