/*
 * Copyright 2002-2007 the original author or authors.
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

package org.springframework.jdbc.support.incrementer;

import org.springframework.dao.DataAccessException;

/**
 * Interface that defines contract of incrementing any data store field's
 * maximum value. Works much like a sequence number generator.
 *
 * <p>Typical implementations may use standard SQL, native RDBMS sequences
 * or Stored Procedures to do the job.
 *
 * @author Dmitriy Kopylenko
 * @author Jean-Pierre Pawlak
 * @author Juergen Hoeller
 */

/**
 * 根据不同数据库对递增主键生成的支持，DataFieldMaxValueIncrementer的相关实现类可以分为如下两类：
 * 1. 基于独立主键表的DataFieldMaxValueIncrementer
 * 依赖于为每一个数据表单独定义的主键表，主键表中定义的主键可以根据需要获取并递增，并且可以设置每次获取的CacheSize以减少访问数据库资源的频度
 * 2. 基于数据库Sequence的DataFieldMaxValueIncrementer
 * 数据库本身支持基于Sequence的主键生成
 */
public interface DataFieldMaxValueIncrementer {

    /**
     * Increment the data store field's max value as int.
     *
     * @return int next data store value such as <b>max + 1</b>
     * @throws org.springframework.dao.DataAccessException in case of errors
     */
    int nextIntValue() throws DataAccessException;

    /**
     * Increment the data store field's max value as long.
     * @return int next data store value such as <b>max + 1</b>
     * @throws org.springframework.dao.DataAccessException in case of errors
     */
    long nextLongValue() throws DataAccessException;

    /**
     * Increment the data store field's max value as String.
     * @return next data store value such as <b>max + 1</b>
     * @throws org.springframework.dao.DataAccessException in case of errors
     */
    String nextStringValue() throws DataAccessException;

}
