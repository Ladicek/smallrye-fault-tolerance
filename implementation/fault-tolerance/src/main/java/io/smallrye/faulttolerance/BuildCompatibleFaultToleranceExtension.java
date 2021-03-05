package io.smallrye.faulttolerance;

import static io.smallrye.faulttolerance.CdiLogger.LOG;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import cdi.lite.extension.BuildCompatibleExtension;
import cdi.lite.extension.Messages;
import cdi.lite.extension.beans.BeanInfo;
import cdi.lite.extension.model.declarations.ClassInfo;
import cdi.lite.extension.model.declarations.MethodInfo;
import cdi.lite.extension.phases.Discovery;
import cdi.lite.extension.phases.Enhancement;
import cdi.lite.extension.phases.Processing;
import cdi.lite.extension.phases.Synthesis;
import cdi.lite.extension.phases.Validation;
import cdi.lite.extension.phases.discovery.AppArchiveBuilder;
import cdi.lite.extension.phases.discovery.MetaAnnotations;
import cdi.lite.extension.phases.enhancement.Annotations;
import cdi.lite.extension.phases.enhancement.ClassConfig;
import cdi.lite.extension.phases.enhancement.ExactType;
import cdi.lite.extension.phases.enhancement.SubtypesOf;
import cdi.lite.extension.phases.synthesis.SyntheticComponents;
import cdi.lite.lifecycle.AfterStartup;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import io.smallrye.faulttolerance.internal.StrategyCache;
import io.smallrye.faulttolerance.metrics.MetricsProvider;

//@SkipIfPortableExtensionPresent(FaultToleranceExtension.class)
public class BuildCompatibleFaultToleranceExtension implements BuildCompatibleExtension {
    private static final Set<String> FAULT_TOLERANCE_ANNOTATIONS = new HashSet<>(Arrays.asList(
            Asynchronous.class.getName(),
            Bulkhead.class.getName(),
            CircuitBreaker.class.getName(),
            Fallback.class.getName(),
            Retry.class.getName(),
            Timeout.class.getName()));

    private final Set<String> faultToleranceClasses = new HashSet<>();
    private final Map<String, Set<MethodInfo<?>>> existingCircuitBreakerNames = new HashMap<>();

    @Discovery
    void registerInterceptorBindings(AppArchiveBuilder app, MetaAnnotations meta) {
        LOG.activated(getImplementationVersion().orElse("unknown"));

        app.add(FaultToleranceInterceptor.class.getName());
        app.add(DefaultFallbackHandlerProvider.class.getName());
        app.add(DefaultAsyncExecutorProvider.class.getName());
        app.add(ExecutorHolder.class.getName());
        app.add(DefaultFaultToleranceOperationProvider.class.getName());
        app.add(DefaultExistingCircuitBreakerNames.class.getName());
        app.add(MetricsProvider.class.getName());
        app.add(StrategyCache.class.getName());
        app.add(CircuitBreakerMaintenanceImpl.class.getName());
        app.add(RequestContextIntegration.class.getName());

        meta.addInterceptorBinding(Asynchronous.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
        meta.addInterceptorBinding(Bulkhead.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
        meta.addInterceptorBinding(CircuitBreaker.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
        meta.addInterceptorBinding(Fallback.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
        meta.addInterceptorBinding(Retry.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
        meta.addInterceptorBinding(Timeout.class, clazz -> clazz.addAnnotation(FaultToleranceBinding.class));
    }

    @Enhancement
    @ExactType(type = FaultToleranceInterceptor.class)
    void changeInterceptorPriority(ClassConfig clazz, Annotations ann) {
        ConfigProvider.getConfig()
                .getOptionalValue("mp.fault.tolerance.interceptor.priority", Integer.class)
                .ifPresent(configuredInterceptorPriority -> {
                    clazz.removeAnnotation(it -> it.name().equals(Priority.class.getName()));
                    clazz.addAnnotation(Priority.class, ann.attribute("value", configuredInterceptorPriority));
                });
    }

    @Processing
    @SubtypesOf(type = Object.class)
    void collectFaultToleranceOperations(BeanInfo<?> bean) {
        if (!bean.isClassBean()) {
            return;
        }

        if (hasFaultToleranceAnnotations(bean.declaringClass())) {
            faultToleranceClasses.add(bean.declaringClass().name());
        }

        for (MethodInfo<?> method : bean.declaringClass().methods()) {
            if (method.hasAnnotation(CircuitBreakerName.class)) {
                String cbName = method.annotation(CircuitBreakerName.class).value().asString();
                existingCircuitBreakerNames.computeIfAbsent(cbName, ignored -> new HashSet<>())
                        .add(method);
            }
        }
    }

    @Synthesis
    void registerSyntheticBeans(SyntheticComponents syn) {
        String[] classesArray = faultToleranceClasses.toArray(new String[0]);
        syn.addBean(BuildCompatibleFaultToleranceOperationProvider.class)
                .type(FaultToleranceOperationProvider.class)
                .type(BuildCompatibleFaultToleranceOperationProvider.class)
                .scope(Singleton.class)
                .alternative(true)
                .priority(1)
                .withParam("classes", classesArray)
                .createWith(BuildCompatibleFaultToleranceOperationProvider.Creator.class);

        syn.addObserver()
                .declaringClass(BuildCompatibleFaultToleranceOperationProvider.class)
                .type(AfterStartup.class)
                .observeWith(BuildCompatibleFaultToleranceOperationProvider.EagerInitializationTrigger.class);

        String[] circuitBreakersArray = existingCircuitBreakerNames.keySet().toArray(new String[0]);
        syn.addBean(BuildCompatibleExistingCircuitBreakerNames.class)
                .type(ExistingCircuitBreakerNames.class)
                .type(BuildCompatibleExistingCircuitBreakerNames.class)
                .scope(Singleton.class)
                .alternative(true)
                .priority(1)
                .withParam("names", circuitBreakersArray)
                .createWith(BuildCompatibleExistingCircuitBreakerNames.Creator.class);
    }

    @Validation
    void validate(Messages msg) {
        for (Map.Entry<String, Set<MethodInfo<?>>> entry : existingCircuitBreakerNames.entrySet()) {
            if (entry.getValue().size() > 1) {
                Set<String> methodNames = entry.getValue()
                        .stream()
                        .map(it -> it.declaringClass().name() + "." + it.name())
                        .collect(Collectors.toSet());
                msg.error(LOG.multipleCircuitBreakersWithTheSameName(entry.getKey(), methodNames));
            }
        }
    }

    static boolean hasFaultToleranceAnnotations(ClassInfo<?> clazz) {
        if (clazz.hasAnnotation(it -> FAULT_TOLERANCE_ANNOTATIONS.contains(it.name()))) {
            return true;
        }

        for (MethodInfo<?> method : clazz.methods()) {
            if (method.hasAnnotation(it -> FAULT_TOLERANCE_ANNOTATIONS.contains(it.name()))) {
                return true;
            }
        }

        ClassInfo<?> superClass = clazz.superClassDeclaration();
        if (superClass != null) {
            return hasFaultToleranceAnnotations(superClass);
        }

        return false;
    }

    private static Optional<String> getImplementationVersion() {
        return AccessController.doPrivileged(new PrivilegedAction<Optional<String>>() {
            @Override
            public Optional<String> run() {
                Properties properties = new Properties();
                try {
                    InputStream resource = this.getClass().getClassLoader()
                            .getResourceAsStream("smallrye-fault-tolerance.properties");
                    if (resource != null) {
                        properties.load(resource);
                        return Optional.ofNullable(properties.getProperty("version"));
                    }
                } catch (IOException e) {
                    LOG.debug("Unable to detect SmallRye Fault Tolerance version");
                }
                return Optional.empty();
            }
        });
    }
}
