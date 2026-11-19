package com.study.template.scope;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Registers the {@code test} scope and its captive-dependency guard. Declare it as a {@code static @Bean}: a
 * {@link BeanFactoryPostProcessor} runs before ordinary beans exist, so it must not need its configuration class.
 */
public final class TestScopeConfigurer implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.registerScope(TestScope.NAME, new TestScope());
        beanFactory.addBeanPostProcessor(new CaptiveDependencyGuard(beanFactory));
    }
}
