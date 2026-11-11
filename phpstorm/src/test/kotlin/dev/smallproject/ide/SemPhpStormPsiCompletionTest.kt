package dev.smallproject.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.MethodReference

/** Real PHP PSI / Ctrl+Space completion. All PHP source is synthetic. */
class SemPhpStormPsiCompletionTest : BasePlatformTestCase() {
    private fun php(value: String): String = value.trimIndent().replace('@', '$')

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "EntityManager/BaseManager.php",
            php("""
                <?php
                namespace Synthetic\Manager;
                abstract class BaseManager {
                    public function commonManagerMethod(): void {}
                }
            """),
        )
        myFixture.addFileToProject(
            "EntityManager/CatalogManager.php",
            php("""
                <?php
                namespace Synthetic\Manager;
                final class CatalogManager extends BaseManager {
                    public function catalogSpecific(): void {}
                }
            """),
        )
        myFixture.addFileToProject(
            "EntityManager/OrderManager.php",
            php("""
                <?php
                namespace Synthetic\Manager;
                final class OrderManager extends BaseManager {
                    public function orderSpecific(): void {}
                }
            """),
        )
        myFixture.addFileToProject(
            "CoreFactory.php",
            php("""
                <?php
                namespace Small\SwooleEntityManager\Factory;
                class EntityManagerFactory {
                    public function get(string @manager, bool @new = false): \Synthetic\Manager\BaseManager {
                        throw new \LogicException('Synthetic static fixture only');
                    }
                }
            """),
        )
        myFixture.addFileToProject(
            "BundleFactory.php",
            php("""
                <?php
                namespace Small\SwooleEntityManagerBundle\Contract;
                interface EntityManagerFactoryInterface {
                    public function get(string @managerClass, bool @new = false): \Synthetic\Manager\BaseManager;
                }
            """),
        )
    }

    private fun suggestions(source: String): Set<String> {
        myFixture.configureByText("Controller.php", php(source))
        val popup = myFixture.completeBasic()?.map { it.lookupString }?.toSet().orEmpty()
        val inserted = myFixture.editor.document.text
        return popup + if ("->catalogSpecific" in inserted) setOf("catalogSpecific") else emptySet()
    }

    fun testCoreFactoryProvidesConcreteManagerPopup() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Small\SwooleEntityManager\Factory\EntityManagerFactory;
            class Controller {
                function run(EntityManagerFactory @factory): void {
                    @manager = @factory->get(CatalogManager::class);
                    @manager-><caret>
                }
            }
        """)
        assertTrue("catalogSpecific not suggested: $results", "catalogSpecific" in results)
    }

    fun testBundleFactoryNamedReorderedProvidesConcreteManagerPopup() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager as Stock;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface;
            class Controller {
                function __construct(private readonly EntityManagerFactoryInterface @factory) {}
                function run(): void {
                    @manager = @this->factory->get(new: true, managerClass: Stock::class);
                    @manager-><caret>
                }
            }
        """)
        assertTrue("catalogSpecific not suggested: $results", "catalogSpecific" in results)
    }

    fun testAccessorReturningAbstractIsCompletedAsConcreteManager() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Synthetic\Manager\BaseManager;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface;
            class Controller {
                function __construct(private readonly EntityManagerFactoryInterface @factory) {}
                private function catalogManager(): BaseManager {
                    return @this->factory->get(CatalogManager::class);
                }
                function run(): void {
                    @this->catalogManager()-><caret>
                }
            }
        """)
        assertTrue("catalogSpecific not suggested: $results", "catalogSpecific" in results)
    }

    fun testOtherManagerCannotOfferCatalogMethod() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\OrderManager;
            use Small\SwooleEntityManager\Factory\EntityManagerFactory;
            class Controller {
                function run(EntityManagerFactory @factory): void {
                    @manager = @factory->get(OrderManager::class);
                    @manager-><caret>
                }
            }
        """)
        assertFalse("catalogSpecific must not be offered: $results", "catalogSpecific" in results)
        assertTrue("orderSpecific must be offered: $results", "orderSpecific" in results)
    }

    fun testDirectFactoryChainCompletesConcreteManager() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Small\SwooleEntityManager\Factory\EntityManagerFactory;
            class Controller {
                function run(EntityManagerFactory @factory): void {
                    @factory->get(CatalogManager::class)-><caret>
                }
            }
        """)
        assertTrue("direct chain should resolve concrete manager: $results", "catalogSpecific" in results)
    }

    fun testDelegatedAccessorCompletesConcreteManager() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Synthetic\Manager\BaseManager;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface;
            class Controller {
                function __construct(private readonly EntityManagerFactoryInterface @factory) {}
                private function catalogManager(): BaseManager {
                    return @this->factory->get(CatalogManager::class);
                }
                private function delegatedManager(): BaseManager {
                    return @this->catalogManager();
                }
                function run(): void {
                    @this->delegatedManager()-><caret>
                }
            }
        """)
        assertTrue("delegation should resolve concrete manager: $results", "catalogSpecific" in results)
    }

    fun testNullableAccessorDoesNotInventConcreteManager() {
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Synthetic\Manager\BaseManager;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface;
            class Controller {
                function __construct(private readonly EntityManagerFactoryInterface @factory) {}
                private function catalogManager(): ?BaseManager {
                    if (random_int(0, 1)) return null;
                    return @this->factory->get(CatalogManager::class);
                }
                function run(): void {
                    @this->catalogManager()-><caret>
                }
            }
        """)
        assertFalse("nullable accessor must not claim concrete type: $results", "catalogSpecific" in results)
    }

    fun testForeignFactoryCannotInferConcreteManager() {
        myFixture.addFileToProject(
            "ForeignCacheFactory.php",
            php("""
                <?php
                namespace Foreign;
                class CacheFactory {
                    public function get(string @manager): \Synthetic\Manager\BaseManager {
                        throw new \LogicException('Foreign factory');
                    }
                }
            """),
        )
        val results = suggestions("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Foreign\CacheFactory;
            class Controller {
                function run(CacheFactory @factory): void {
                    @manager = @factory->get(CatalogManager::class);
                    @manager-><caret>
                }
            }
        """)
        assertFalse("foreign factory must not claim concrete manager: $results", "catalogSpecific" in results)
    }

    fun testManagerGetTypeIsConcreteWithoutAbstractUnion() {
        myFixture.configureByText("Controller.php", php("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager;
            use Small\SwooleEntityManager\Factory\EntityManagerFactory;
            class Controller {
                function run(EntityManagerFactory @factory): void {
                    @manager = @factory->get(CatalogManager::class);
                    @manager->catalogSpecific();
                }
            }
        """))
        val get = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .first { it.name == "get" }
        val types = get.type.types
        assertTrue("Concrete manager type must be present: $types",
            types.any { it.contains("CatalogManager") })
        assertFalse("Abstract base manager must not be in the result: $types",
            types.any { it.contains("BaseManager") })
    }

    fun testNamedBundleFactoryGetTypeIsConcreteWithoutAbstractUnion() {
        myFixture.configureByText("Controller.php", php("""
            <?php
            namespace Synthetic\Demo;
            use Synthetic\Manager\CatalogManager as Stock;
            use Small\SwooleEntityManagerBundle\Contract\EntityManagerFactoryInterface;
            class Controller {
                function run(EntityManagerFactoryInterface @factory): void {
                    @manager = @factory->get(new: true, managerClass: Stock::class);
                    @manager->catalogSpecific();
                }
            }
        """))
        val get = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .first { it.name == "get" }
        val types = get.type.types
        assertTrue("Concrete bundle manager type must be present: $types",
            types.any { it.contains("CatalogManager") })
        assertFalse("Abstract base manager must not be in bundle result: $types",
            types.any { it.contains("BaseManager") })
    }

}
