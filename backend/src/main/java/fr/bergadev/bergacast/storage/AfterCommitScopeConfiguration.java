package fr.bergadev.bergacast.storage;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps the transactional beans, and those marked {@link AfterCommitScope}, in an {@link AfterCommit} scope. The advice
 * is ordered before the transaction interceptor so that it surrounds the transaction: the queued actions run after it
 * has completed and released its connection.
 */
@Configuration(proxyBeanMethods = false)
class AfterCommitScopeConfiguration {

    /** Before the transaction advisor, which has the lowest precedence, so that this one is the outer one. */
    private static final int OUTSIDE_THE_TRANSACTION = 0;

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DefaultPointcutAdvisor afterCommitScopeAdvisor() {
        Pointcut scoped = new ComposablePointcut(new AnnotationMatchingPointcut(Transactional.class, true))
                .union(AnnotationMatchingPointcut.forMethodAnnotation(Transactional.class))
                .union(new AnnotationMatchingPointcut(AfterCommitScope.class, true));
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(scoped, afterCommitScopeInterceptor());
        advisor.setOrder(OUTSIDE_THE_TRANSACTION);
        return advisor;
    }

    private static MethodInterceptor afterCommitScopeInterceptor() {
        return invocation -> {
            if (!AfterCommit.openScope()) {
                return invocation.proceed();
            }
            // Also on failure: the actions of the transactions committed before it must still run.
            try {
                return invocation.proceed();
            } finally {
                AfterCommit.closeScope();
            }
        };
    }
}
