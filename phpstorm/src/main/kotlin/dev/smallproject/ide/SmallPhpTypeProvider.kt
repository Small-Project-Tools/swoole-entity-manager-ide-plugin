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
        val receiver = reference.classReference?.text ?: return null
        if (name != "get" && (receiver != "\$this" || reference.parameters.isNotEmpty())) return null
        val file = element.containingFile ?: return null
        val source = file.text
        val offset = element.textOffset
        val concrete = if (name == "get") {
            val argument = SemTypeInference.managerArgument(reference.parameters.map { it.text })
                ?: return null
            SemTypeInference.factoryResult(source, offset, argument, receiver)
        } else {
            // Only a locally defined zero-argument $this->accessor() that
            // provably returns the same concrete manager in every return.
            // Never infer a type for arbitrary methods in other classes.
            SemManagerAccessorInference.infer(source, offset, name)
        } ?: return null
        return PhpType().add(concrete)
    }

    override fun complete(expression: String, project: Project): PhpType? = null

    override fun getBySignature(
        expression: String,
        visited: MutableSet<String>,
        depth: Int,
        project: Project,
    ): Collection<PhpNamedElement>? = null
}
