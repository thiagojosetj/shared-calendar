package io.github.thiagojosetj.sharedcalendar;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * Regras de arquitetura verificadas no build (ADR-0001).
 *
 * <p>A organização por módulos só se mantém se quebrá-la quebrar o build. Revisão de código esquece;
 * teste não. Estas regras rodam em {@code ./mvnw test}, sem Docker, e analisam apenas o código de
 * produção.
 *
 * <p>Algumas regras miram pacotes que ainda não existem (por exemplo, {@code ..domain..}). Elas usam
 * {@code allowEmptyShould(true)} para não falhar enquanto o pacote está vazio, e passam a valer
 * automaticamente quando o primeiro módulo for criado.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String ROOT = "io.github.thiagojosetj.sharedcalendar";

    /** Pacotes de primeiro nível que não são módulos de domínio. */
    private static final Set<String> NON_MODULES = Set.of("config", "shared");

    /** Camadas de um módulo que outro módulo nunca pode acessar diretamente. */
    private static final Set<String> MODULE_PRIVATE_LAYERS = Set.of("api", "persistence");

    @ArchTest
    static final ArchRule shared_nao_depende_do_restante_da_aplicacao = noClasses()
            .that().resideInAPackage(ROOT + ".shared..")
            .should().dependOnClassesThat(
                    resideInAPackage(ROOT + "..").and(not(resideInAPackage(ROOT + ".shared.."))))
            .because("shared pode ser usado por todos os módulos, então não pode depender de nenhum deles");

    @ArchTest
    static final ArchRule modulos_nao_acessam_api_nem_persistence_de_outro_modulo = classes()
            .should(notAccessApiOrPersistenceOfAnotherModule())
            .because("módulos se comunicam pela camada application ou por eventos, nunca pelos internos"
                    + " de outro módulo (ADR-0001)");

    @ArchTest
    static final ArchRule domain_nao_depende_de_outras_camadas = noClasses()
            .that().resideInAPackage(ROOT + ".*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".*.api..",
                    ROOT + ".*.application..",
                    ROOT + ".*.persistence..",
                    "org.springframework.web..",
                    "jakarta.servlet..")
            .because("o domínio concentra as regras de negócio e não conhece HTTP nem a orquestração"
                    + " dos casos de uso (ADR-0001)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_nao_depende_da_camada_api = noClasses()
            .that().resideInAPackage(ROOT + ".*.application..")
            .should().dependOnClassesThat().resideInAPackage(ROOT + ".*.api..")
            .because("a dependência vai de api para application, nunca o contrário (ADR-0001)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule nenhum_campo_usa_LocalDateTime = noFields()
            .should().haveRawType(LocalDateTime.class)
            .because("LocalDateTime não tem fuso e é ambíguo em um calendário com usuários em fusos"
                    + " diferentes; use Instant ou LocalDate + LocalTime + ZoneId (ADR-0002)");

    @ArchTest
    static final ArchRule nenhum_metodo_retorna_LocalDateTime = noMethods()
            .should().haveRawReturnType(LocalDateTime.class)
            .because("LocalDateTime não tem fuso e é ambíguo (ADR-0002)");

    @ArchTest
    static final ArchRule horario_atual_so_vem_do_clock_injetado = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".config..")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .because("regras que dependem de \"agora\" precisam receber o Clock injetado para serem"
                    + " testáveis de forma determinística (RN-TZ-08, ADR-0002)");

    private static ArchCondition<JavaClass> notAccessApiOrPersistenceOfAnotherModule() {
        return new ArchCondition<>("not access api or persistence of another module") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> originModule = moduleOf(origin);
                if (originModule.isEmpty()) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    Optional<String> targetModule = moduleOf(target);
                    boolean otherModule = targetModule.isPresent()
                            && !targetModule.get().equals(originModule.get());
                    if (otherModule && MODULE_PRIVATE_LAYERS.contains(layerOf(target))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** {@code ROOT.events.api.EventController} pertence ao módulo {@code events}. */
    private static Optional<String> moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT + ".")) {
            return Optional.empty();
        }
        String module = packageName.substring(ROOT.length() + 1).split("\\.")[0];
        return NON_MODULES.contains(module) ? Optional.empty() : Optional.of(module);
    }

    /** {@code ROOT.events.api.EventController} está na camada {@code api}. */
    private static String layerOf(JavaClass javaClass) {
        String[] segments = javaClass.getPackageName().substring(ROOT.length() + 1).split("\\.");
        return segments.length > 1 ? segments[1] : "";
    }
}
