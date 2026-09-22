package pe.com.scotiabank.tapp.service.registration.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "pe.com.scotiabank.tapp.service.registration.api",
    importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases")
class HexagonalArchitectureTest {

  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..adapters..",
              "org.springframework..",
              "org.apache.kafka..",
              "org.springframework.data..");

  @ArchTest
  static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapters..");
}
