package io.github.trimax.raven.core;

import static com.tngtech.archunit.core.domain.JavaModifier.PROTECTED;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.modifier;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Keeps the implementation behind the API: the exported packages never expose a type of
 * {@code io.github.trimax.raven.core.internal}, which module-info does not export.
 */
@AnalyzeClasses(packages = "io.github.trimax.raven.core", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiEncapsulationTest {

    private static final String INTERNAL = "io.github.trimax.raven.core.internal..";

    @ArchTest
    static final ArchRule apiSignaturesDoNotExposeInternalTypes = codeUnits()
            .that(modifier(PUBLIC).or(modifier(PROTECTED)))
            .and().areDeclaredInClassesThat().resideOutsideOfPackage(INTERNAL)
            .should(notExposeInternalTypes());

    @ArchTest
    static final ArchRule apiFieldsDoNotExposeInternalTypes = fields()
            .that(modifier(PUBLIC).or(modifier(PROTECTED)))
            .and().areDeclaredInClassesThat().resideOutsideOfPackage(INTERNAL)
            .should(notHaveAnInternalType())
            // The API has no public fields today; the rule guards against the first one
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule internalDoesNotDependOnSpringModules = noClasses()
            .that().resideInAPackage(INTERNAL)
            .should().dependOnClassesThat().resideInAnyPackage("io.github.trimax.raven.spring..",
                    "io.github.trimax.raven.server..", "io.github.trimax.raven.client..");

    private static ArchCondition<JavaCodeUnit> notExposeInternalTypes() {
        return new ArchCondition<>("not have an internal parameter or return type") {
            @Override
            public void check(final JavaCodeUnit codeUnit, final ConditionEvents events) {
                final List<JavaClass> types = new ArrayList<>(codeUnit.getRawParameterTypes());
                types.add(codeUnit.getRawReturnType());

                for (final var type : types)
                    if (isInternal(type))
                        events.add(SimpleConditionEvent.violated(codeUnit,
                                codeUnit.getFullName() + " exposes internal type " + type.getName()));
            }
        };
    }

    private static ArchCondition<JavaField> notHaveAnInternalType() {
        return new ArchCondition<>("not have an internal type") {
            @Override
            public void check(final JavaField field, final ConditionEvents events) {
                if (isInternal(field.getRawType()))
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " exposes internal type " + field.getRawType().getName()));
            }
        };
    }

    private static boolean isInternal(final JavaClass type) {
        return type.getBaseComponentType().getPackageName().startsWith("io.github.trimax.raven.core.internal");
    }
}
