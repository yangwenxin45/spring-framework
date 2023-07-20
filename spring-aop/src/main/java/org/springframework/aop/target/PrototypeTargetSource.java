/*
 * Copyright 2002-2014 the original author or authors.
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

package org.springframework.aop.target;

import org.springframework.beans.BeansException;

/**
 * {@link org.springframework.aop.TargetSource} implementation that
 * creates a new instance of the target bean for each request,
 * destroying each instance on release (after each request).
 *
 * <p>Obtains bean instances from its containing
 * {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #setBeanFactory
 * @see #setTargetBeanName
 */

/**
 * PrototypeTargetSource每次都需要返回新的对象实例，需要注意两点：
 * 1. 目标对象的bean定义声明的scope必须为prototype
 * 2. 通过targetBeanName属性指定目标对象的bean定义名称，而不是引用
 */
@SuppressWarnings("serial")
public class PrototypeTargetSource extends AbstractPrototypeBasedTargetSource {

    /**
     * Obtain a new prototype instance for every call.
     *
     * @see #newPrototypeInstance()
     */
    @Override
    public Object getTarget() throws BeansException {
        return newPrototypeInstance();
    }

    /**
     * Destroy the given independent instance.
     * @see #destroyPrototypeInstance
     */
    @Override
    public void releaseTarget(Object target) {
        destroyPrototypeInstance(target);
    }

    @Override
    public String toString() {
        return "PrototypeTargetSource for target bean with name '" + getTargetBeanName() + "'";
    }

}
