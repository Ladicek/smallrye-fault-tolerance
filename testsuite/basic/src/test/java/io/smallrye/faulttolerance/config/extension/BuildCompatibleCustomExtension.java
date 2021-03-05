package io.smallrye.faulttolerance.config.extension;

import java.time.temporal.ChronoUnit;

import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.faulttolerance.Retry;

import cdi.lite.extension.BuildCompatibleExtension;
import cdi.lite.extension.phases.Enhancement;
import cdi.lite.extension.phases.enhancement.ClassConfig;
import cdi.lite.extension.phases.enhancement.ExactType;

public class BuildCompatibleCustomExtension implements BuildCompatibleExtension {
    @ExactType(type = UnconfiguredService.class)
    @Enhancement
    void configureService(ClassConfig clazz) {
        clazz.addAnnotation(new RetryLiteral());
    }

    public static class RetryLiteral extends AnnotationLiteral<Retry> implements Retry {
        @Override
        public int maxRetries() {
            return 2;
        }

        @Override
        public long delay() {
            return 0;
        }

        @Override
        public ChronoUnit delayUnit() {
            return ChronoUnit.MILLIS;
        }

        @Override
        public long maxDuration() {
            return Long.MAX_VALUE;
        }

        @Override
        public ChronoUnit durationUnit() {
            return ChronoUnit.NANOS;
        }

        @Override
        public long jitter() {
            return 0;
        }

        @Override
        public ChronoUnit jitterDelayUnit() {
            return ChronoUnit.MILLIS;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends Throwable>[] retryOn() {
            return new Class[] { Exception.class };
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends Throwable>[] abortOn() {
            return new Class[] {};
        }
    }
}
