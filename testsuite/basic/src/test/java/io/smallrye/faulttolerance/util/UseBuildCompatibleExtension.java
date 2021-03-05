package io.smallrye.faulttolerance.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cdi.lite.extension.BuildCompatibleExtension;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(UseBuildCompatibleExtensionJunit.class)
public @interface UseBuildCompatibleExtension {
    Class<? extends BuildCompatibleExtension> value();
}
