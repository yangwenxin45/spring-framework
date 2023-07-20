/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.dao;

import org.springframework.lang.Nullable;

/**
 * Normal superclass when we can't distinguish anything more specific
 * than "something went wrong with the underlying resource": for example,
 * a SQLException from JDBC we can't pinpoint more precisely.
 *
 * @author Rod Johnson
 */

/**
 * 出现其他无法详细分类的数据访问异常，可以抛出UncategorizedScriptException
 * 该异常定义为abstract，如果对于特定的数据访问方式来说，以上的异常类型无法描述当前数据访问方式中特定的异常情况，
 * 那么可以通过扩展UncategorizedDataAccessException来进一步细化特定的数据访问异常类型
 */
@SuppressWarnings("serial")
public abstract class UncategorizedDataAccessException extends NonTransientDataAccessException {

    /**
     * Constructor for UncategorizedDataAccessException.
     *
     * @param msg   the detail message
     * @param cause the exception thrown by underlying data access API
     */
    public UncategorizedDataAccessException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }

}
