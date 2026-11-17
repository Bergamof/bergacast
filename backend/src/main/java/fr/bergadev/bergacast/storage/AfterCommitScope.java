package fr.bergadev.bergacast.storage;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean that manages its own transactions (e.g. with a {@code TransactionTemplate}) and registers actions with
 * {@link AfterCommit}: its public methods run them once the transaction is over. Beans with {@code @Transactional}
 * methods need no mark: they are in scope already.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AfterCommitScope {}
