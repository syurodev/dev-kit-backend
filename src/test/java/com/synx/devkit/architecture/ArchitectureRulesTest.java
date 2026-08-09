package com.synx.devkit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.synx.devkit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {
    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "..adapter..");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS_OR_SPRING = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "..adapter..");

    @ArchTest
    static final ArchRule INBOUND_DOES_NOT_CALL_OUTBOUND_ADAPTERS = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..");
}
