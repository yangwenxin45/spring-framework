/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction;

import org.springframework.lang.Nullable;

import java.sql.Connection;

/**
 * Interface that defines Spring-compliant transaction properties.
 * Based on the propagation behavior definitions analogous to EJB CMT attributes.
 *
 * <p>Note that isolation level and timeout settings will not get applied unless
 * an actual new transaction gets started. As only {@link #PROPAGATION_REQUIRED},
 * {@link #PROPAGATION_REQUIRES_NEW} and {@link #PROPAGATION_NESTED} can cause
 * that, it usually doesn't make sense to specify those settings in other cases.
 * Furthermore, be aware that not all transaction managers will support those
 * advanced features and thus might throw corresponding exceptions when given
 * non-default values.
 *
 * <p>The {@link #isReadOnly() read-only flag} applies to any transaction context,
 * whether backed by an actual resource transaction or operating non-transactionally
 * at the resource level. In the latter case, the flag will only apply to managed
 * resources within the application, such as a Hibernate {@code Session}.
 *
 * @author Juergen Hoeller
 * @since 08.05.2003
 * @see PlatformTransactionManager#getTransaction(TransactionDefinition)
 * @see org.springframework.transaction.support.DefaultTransactionDefinition
 * @see org.springframework.transaction.interceptor.TransactionAttribute
 */
// TransactionDefinition负责定义事务相关属性，包括隔离级别、传播行为、超时时间、是否为只读事务等
// 事务的传播行为表示整个事务处理过程所跨越的业务对象，将以什么样的行为参与事务（我们在声明式事务中更多地依赖于该属性）
public interface TransactionDefinition {

    /**
     * Support a current transaction; create a new one if none exists.
     * Analogous to the EJB transaction attribute of the same name.
     * <p>This is typically the default setting of a transaction definition,
     * and typically defines a transaction synchronization scope.
     */
    // 如果当前存在一个事务，则加入当前事务。如果不存在事务，则创建一个新的事务
    // PROPAGATION_REQUIRED通常作为默认的事务传播行为
    int PROPAGATION_REQUIRED = 0;

	/**
	 * Support a current transaction; execute non-transactionally if none exists.
	 * Analogous to the EJB transaction attribute of the same name.
	 * <p><b>NOTE:</b> For transaction managers with transaction synchronization,
	 * {@code PROPAGATION_SUPPORTS} is slightly different from no transaction
	 * at all, as it defines a transaction scope that synchronization might apply to.
	 * As a consequence, the same resources (a JDBC {@code Connection}, a
	 * Hibernate {@code Session}, etc) will be shared for the entire specified
	 * scope. Note that the exact behavior depends on the actual synchronization
     * configuration of the transaction manager!
     * <p>In general, use {@code PROPAGATION_SUPPORTS} with care! In particular, do
     * not rely on {@code PROPAGATION_REQUIRED} or {@code PROPAGATION_REQUIRES_NEW}
     * <i>within</i> a {@code PROPAGATION_SUPPORTS} scope (which may lead to
     * synchronization conflicts at runtime). If such nesting is unavoidable, make sure
     * to configure your transaction manager appropriately (typically switching to
     * "synchronization on actual transaction").
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setTransactionSynchronization
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
     */
    /**
     * 如果当前存在一个事务，则加入当前事务。如果当前不存在事务，则直接执行
     * 使用PROPAGATION_SUPPORTS可以保证当前方法能够加入当前事务，并洞察当前方法对数据资源所做的更新
     */
    int PROPAGATION_SUPPORTS = 1;

    /**
     * Support a current transaction; throw an exception if no current transaction
     * exists. Analogous to the EJB transaction attribute of the same name.
     * <p>Note that transaction synchronization within a {@code PROPAGATION_MANDATORY}
     * scope will always be driven by the surrounding transaction.
     */
    /**
     * PROPAGATION_MANDATORY强制要求当前存在一个事务，如果不存在，则抛出异常
     * 如果某个方法需要事务支持，但自身又不管理事务提交或者回滚，那么比较适合使用PROPAGATION_MANDATORY
     */
    int PROPAGATION_MANDATORY = 2;

    /**
     * Create a new transaction, suspending the current transaction if one exists.
     * Analogous to the EJB transaction attribute of the same name.
     * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
     * on all transaction managers. This in particular applies to
     * {@link org.springframework.transaction.jta.JtaTransactionManager},
     * which requires the {@code javax.transaction.TransactionManager} to be
     * made available it to it (which is server-specific in standard Java EE).
     * <p>A {@code PROPAGATION_REQUIRES_NEW} scope always defines its own
     * transaction synchronizations. Existing synchronizations will be suspended
     * and resumed appropriately.
     * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
     */
    /**
     * 不管当前是否存在事务，都会创建新的事务，如果当前存在事务，会将当前存在的事务挂起
     * 如果某个业务对象所做的事情不想影响到外层事务，比如更新日志，那么PROPAGATION_REQUIRES_NEW应该是合适的选择
     */
    int PROPAGATION_REQUIRES_NEW = 3;

    /**
     * Do not support a current transaction; rather always execute non-transactionally.
     * Analogous to the EJB transaction attribute of the same name.
     * <p><b>NOTE:</b> Actual transaction suspension will not work out-of-the-box
     * on all transaction managers. This in particular applies to
     * {@link org.springframework.transaction.jta.JtaTransactionManager},
     * which requires the {@code javax.transaction.TransactionManager} to be
     * made available it to it (which is server-specific in standard Java EE).
     * <p>Note that transaction synchronization is <i>not</i> available within a
     * {@code PROPAGATION_NOT_SUPPORTED} scope. Existing synchronizations
     * will be suspended and resumed appropriately.
     * @see org.springframework.transaction.jta.JtaTransactionManager#setTransactionManager
     */
    /**
     * 不支持当前事务，而是在没有事务的情况下执行
     * 如果当前存在事务的话，当前事务原则上将被挂起，但这要看对应的PlatformTransactionManager是否支持事务的挂起
     */
    int PROPAGATION_NOT_SUPPORTED = 4;

    /**
     * Do not support a current transaction; throw an exception if a current transaction
     * exists. Analogous to the EJB transaction attribute of the same name.
     * <p>Note that transaction synchronization is <i>not</i> available within a
     * {@code PROPAGATION_NEVER} scope.
     */
    // 永远不需要当前存在事务，如果当前存在事务，则抛出异常
    int PROPAGATION_NEVER = 5;

    /**
     * Execute within a nested transaction if a current transaction exists,
     * behave like {@link #PROPAGATION_REQUIRED} otherwise. There is no
     * analogous feature in EJB.
     * <p><b>NOTE:</b> Actual creation of a nested transaction will only work on
     * specific transaction managers. Out of the box, this only applies to the JDBC
     * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager}
     * when working on a JDBC 3.0 driver. Some JTA providers might support
     * nested transactions as well.
     * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
     */
    /**
     * 如果当前存在事务，则在当前事务的一个嵌套事务中执行，否则创建新的事务，在新创建的事务中执行
     * PROPAGATION_NESTED可能的应用场景在于，你可以将一个大的事务划分为多个小的事务来处理，并且外层事务可以根据各个内部嵌套事务的执行结果，来选择不通的执行流程
     */
    int PROPAGATION_NESTED = 6;


    /**
     * Use the default isolation level of the underlying datastore.
     * All other levels correspond to the JDBC isolation levels.
     *
     * @see java.sql.Connection
     */
    // 表示使用数据库的默认隔离级别，通常情况下是Read Committed
	int ISOLATION_DEFAULT = -1;

	/**
	 * Indicates that dirty reads, non-repeatable reads and phantom reads
	 * can occur.
	 * <p>This level allows a row changed by one transaction to be read by another
	 * transaction before any changes in that row have been committed (a "dirty read").
	 * If any of the changes are rolled back, the second transaction will have
     * retrieved an invalid row.
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
	 */
	// 对应Read Uncommitted隔离级别，无法避免脏读，不可重复读和幻读
	int ISOLATION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED;

	/**
	 * Indicates that dirty reads are prevented; non-repeatable reads and
	 * phantom reads can occur.
	 * <p>This level only prohibits a transaction from reading a row
	 * with uncommitted changes in it.
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
	 */
	// 对应Read Committed隔离级别，可以避免脏读，但无法避免不可重复读和幻读
	int ISOLATION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED;

	/**
	 * Indicates that dirty reads and non-repeatable reads are prevented;
	 * phantom reads can occur.
	 * <p>This level prohibits a transaction from reading a row with uncommitted changes
	 * in it, and it also prohibits the situation where one transaction reads a row,
	 * a second transaction alters the row, and the first transaction re-reads the row,
	 * getting different values the second time (a "non-repeatable read").
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
	 */
	// 对应Repetable read隔离级别，可以避免脏读和不可重复读，但不能避免幻读
	int ISOLATION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ;

	/**
	 * Indicates that dirty reads, non-repeatable reads and phantom reads
	 * are prevented.
	 * <p>This level includes the prohibitions in {@link #ISOLATION_REPEATABLE_READ}
	 * and further prohibits the situation where one transaction reads all rows that
	 * satisfy a {@code WHERE} condition, a second transaction inserts a row
	 * that satisfies that {@code WHERE} condition, and the first transaction
	 * re-reads for the same condition, retrieving the additional "phantom" row
     * in the second read.
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
	 */
	// 对应Serializable隔离级别，可以避免所有的脏读，不可重复读以及幻读，但并发效率最低
	int ISOLATION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE;


	/**
	 * Use the default timeout of the underlying transaction system,
	 * or none if timeouts are not supported.
	 */
	int TIMEOUT_DEFAULT = -1;


	/**
	 * Return the propagation behavior.
	 * <p>Must return one of the {@code PROPAGATION_XXX} constants
	 * defined on {@link TransactionDefinition this interface}.
	 * @return the propagation behavior
	 * @see #PROPAGATION_REQUIRED
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isActualTransactionActive()
	 */
	int getPropagationBehavior();

	/**
	 * Return the isolation level.
	 * <p>Must return one of the {@code ISOLATION_XXX} constants defined on
	 * {@link TransactionDefinition this interface}. Those constants are designed
	 * to match the values of the same constants on {@link java.sql.Connection}.
	 * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
	 * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
	 * transactions. Consider switching the "validateExistingTransactions" flag to
	 * "true" on your transaction manager if you'd like isolation level declarations
	 * to get rejected when participating in an existing transaction with a different
	 * isolation level.
	 * <p>Note that a transaction manager that does not support custom isolation levels
	 * will throw an exception when given any other level than {@link #ISOLATION_DEFAULT}.
	 * @return the isolation level
	 * @see #ISOLATION_DEFAULT
	 * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#setValidateExistingTransaction
	 */
	int getIsolationLevel();

	/**
	 * Return the transaction timeout.
	 * <p>Must return a number of seconds, or {@link #TIMEOUT_DEFAULT}.
	 * <p>Exclusively designed for use with {@link #PROPAGATION_REQUIRED} or
	 * {@link #PROPAGATION_REQUIRES_NEW} since it only applies to newly started
	 * transactions.
	 * <p>Note that a transaction manager that does not support timeouts will throw
	 * an exception when given any other timeout than {@link #TIMEOUT_DEFAULT}.
	 * @return the transaction timeout
	 */
	int getTimeout();

	/**
	 * Return whether to optimize as a read-only transaction.
	 * <p>The read-only flag applies to any transaction context, whether backed
	 * by an actual resource transaction ({@link #PROPAGATION_REQUIRED}/
	 * {@link #PROPAGATION_REQUIRES_NEW}) or operating non-transactionally at
	 * the resource level ({@link #PROPAGATION_SUPPORTS}). In the latter case,
	 * the flag will only apply to managed resources within the application,
	 * such as a Hibernate {@code Session}.
	 * <p>This just serves as a hint for the actual transaction subsystem;
	 * it will <i>not necessarily</i> cause failure of write access attempts.
	 * A transaction manager which cannot interpret the read-only hint will
	 * <i>not</i> throw an exception when asked for a read-only transaction.
	 * @return {@code true} if the transaction is to be optimized as read-only
	 * @see org.springframework.transaction.support.TransactionSynchronization#beforeCommit(boolean)
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#isCurrentTransactionReadOnly()
	 */
	boolean isReadOnly();

	/**
	 * Return the name of this transaction. Can be {@code null}.
	 * <p>This will be used as the transaction name to be shown in a
	 * transaction monitor, if applicable (for example, WebLogic's).
	 * <p>In case of Spring's declarative transactions, the exposed name will be
	 * the {@code fully-qualified class name + "." + method name} (by default).
	 * @return the name of this transaction
	 * @see org.springframework.transaction.interceptor.TransactionAspectSupport
	 * @see org.springframework.transaction.support.TransactionSynchronizationManager#getCurrentTransactionName()
	 */
	@Nullable
	String getName();

}
