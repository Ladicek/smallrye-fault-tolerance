package io.smallrye.faulttolerance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;

import cdi.lite.extension.phases.synthesis.SyntheticBeanCreator;

public class BuildCompatibleExistingCircuitBreakerNames implements ExistingCircuitBreakerNames {
    private Set<String> names;

    void init(Set<String> names) {
        this.names = names;
    }

    @Override
    public boolean contains(String name) {
        return names.contains(name);
    }

    public static class Creator implements SyntheticBeanCreator<BuildCompatibleExistingCircuitBreakerNames> {
        @Override
        public BuildCompatibleExistingCircuitBreakerNames create(
                CreationalContext<BuildCompatibleExistingCircuitBreakerNames> creationalContext,
                InjectionPoint injectionPoint, Map<String, Object> map) {

            String[] existingCircuitBreakerNames = (String[]) map.get("names");

            BuildCompatibleExistingCircuitBreakerNames result = new BuildCompatibleExistingCircuitBreakerNames();
            result.init(new HashSet<>(Arrays.asList(existingCircuitBreakerNames)));
            return result;
        }
    }
}
