package com.study.project.assembly.scope;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Refuses a singleton that depends on a test-scoped bean. Spring does not check this: the singleton would get the
 * instance of whichever test created it first and keep it for the whole run — the captive dependency. Spring's own remedy, a scoped proxy, would
 * hide the mistake instead of reporting it.
 *
 * <p>Checks the direct dependencies Spring recorded while autowiring the bean's constructor or {@code @Bean} method.
 */
final class CaptiveDependencyGuard implements BeanPostProcessor {

    private final ConfigurableListableBeanFactory beanFactory;

    CaptiveDependencyGuard(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (!beanFactory.containsBeanDefinition(name) || !beanFactory.getMergedBeanDefinition(name).isSingleton()) {
            return bean;
        }
        for (String dependency : beanFactory.getDependenciesForBean(name)) {
            if (beanFactory.containsBeanDefinition(dependency) && isTestScoped(beanFactory.getMergedBeanDefinition(dependency))) {
                throw new BeanCreationException(name, "Singleton '" + name + "' depends on test-scoped '" + dependency
                        + "': it would keep the first test's instance for the whole run (captive dependency)."
                        + " Make '" + name + "' test-scoped too, or depend on something that lives for the run.");
            }
        }
        return bean;
    }

    private static boolean isTestScoped(BeanDefinition definition) {
        return TestScope.NAME.equals(definition.getScope());
    }
}
