package io.github.trimax.raven.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Uses raven-core through its API only: {@code io.github.trimax.raven.core.internal} is not exported.
 */
@AnalyzeClasses(packages = "io.github.trimax.raven.server")
class NoInternalDependencyTest {

    @ArchTest
    static final ArchRule noDependencyOnCoreInternals = noClasses()
            .should().dependOnClassesThat().resideInAPackage("io.github.trimax.raven.core.internal..");
}
