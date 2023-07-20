/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.transaction.interceptor;

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;

/**
 * This interface adds a {@code rollbackOn} specification to {@link org.springframework.transaction.TransactionDefinition}.
 * As custom {@code rollbackOn} is only possible with AOP, it resides in the AOP-related
 * transaction subpackage.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16.03.2003
 * @see org.springframework.transaction.interceptor.DefaultTransactionAttribute
 * @see org.springframework.transaction.interceptor.RuleBasedTransactionAttribute
 */

/**
 * 主要面向使用Spring AOP进行声明式事务管理的场合
 * 在TransactionDefinition定义的基础上添加了一个rollbackOn方法，可以通过声明的方式指定业务方法在抛出哪些异常的情况下可以回滚事务
 */
public interface TransactionAttribute extends TransactionDefinition {

    /**
     * Return a qualifier value associated with this transaction attribute.
     * <p>This may be used for choosing a corresponding transaction manager
     * to process this specific transaction.
     *
     * @since 3.0
     */
    @Nullable
    String getQualifier();

    /**
     * Should we roll back on the given exception?
     * @param ex the exception to evaluate
     * @return whether to perform a rollback or not
     */
    boolean rollbackOn(Throwable ex);

}
