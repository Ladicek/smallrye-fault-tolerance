package io.smallrye.faulttolerance.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.weld.junit5.auto.AddExtensions;
import org.junit.jupiter.api.extension.ExtendWith;

import io.smallrye.config.inject.ConfigExtension;
import io.smallrye.metrics.setup.MetricCdiInjectionExtension;
import stilldi.impl.StillDI;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtendWith(WeldWithFaultToleranceExtension.class)
@AddExtensions({ StillDI.class, ConfigExtension.class, MetricCdiInjectionExtension.class })
public @interface FaultToleranceBasicTest {
}
