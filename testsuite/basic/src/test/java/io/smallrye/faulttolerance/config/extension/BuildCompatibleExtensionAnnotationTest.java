package io.smallrye.faulttolerance.config.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.temporal.ChronoUnit;

import javax.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.smallrye.faulttolerance.FaultToleranceOperationProvider;
import io.smallrye.faulttolerance.config.FaultToleranceOperation;
import io.smallrye.faulttolerance.config.RetryConfig;
import io.smallrye.faulttolerance.util.FaultToleranceBasicTest;
import io.smallrye.faulttolerance.util.UseBuildCompatibleExtension;

@FaultToleranceBasicTest
@UseBuildCompatibleExtension(BuildCompatibleCustomExtension.class)
@Disabled // would require serious refactoring of the config system
public class BuildCompatibleExtensionAnnotationTest {
    @Inject
    FaultToleranceOperationProvider ops;

    @Inject
    UnconfiguredService service;

    @Test
    public void testAnnotationAddedByExtension() throws NoSuchMethodException, SecurityException {
        FaultToleranceOperation ping = ops.get(UnconfiguredService.class, UnconfiguredService.class.getMethod("ping"));
        assertThat(ping).isNotNull();
        assertThat(ping.hasRetry()).isTrue();

        RetryConfig fooRetry = ping.getRetry();
        // Method-level
        assertThat(fooRetry.get(RetryConfig.MAX_RETRIES, Integer.class)).isEqualTo(2);
        // Default value
        assertThat(fooRetry.get(RetryConfig.DELAY_UNIT, ChronoUnit.class)).isEqualTo(ChronoUnit.MILLIS);

        UnconfiguredService.COUNTER.set(0);
        assertThatThrownBy(() -> {
            service.ping();
        }).isExactlyInstanceOf(IllegalStateException.class);
        assertThat(UnconfiguredService.COUNTER.get()).isEqualTo(3);
    }
}
