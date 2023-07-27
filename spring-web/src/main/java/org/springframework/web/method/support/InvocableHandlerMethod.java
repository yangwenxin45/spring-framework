/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.web.method.support;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Provides a method for invoking the handler method for a given request after resolving its
 * method argument values through registered {@link org.springframework.web.method.support.HandlerMethodArgumentResolver}s.
 *
 * <p>Argument resolution often requires a {@link WebDataBinder} for data binding or for type
 * conversion. Use the {@link #setDataBinderFactory(WebDataBinderFactory)} property to supply
 * a binder factory to pass to argument resolvers.
 *
 * <p>Use {@link #setHandlerMethodArgumentResolvers} to customize the list of argument resolvers.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.1
 */

/**
 * InvocableHandlerMethod在父类基础上增加了调用的功能，也就是说InvocableHandlerMethod可以直接调用内部属性method对应的方法，
 * 严格来说是bridgedMethod
 *
 * @author yangwenxin
 * @date 2023-07-27 10:41
 */
public class InvocableHandlerMethod extends HandlerMethod {

    // HandlerMethodArgumentResolverComposite类型，用于解析参数
    private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();

    // ParameterNameDiscoverer类型，用来获取参数名，用于MethodParameter中
    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    // WebDataBinderFactory类型，可以创建WebDataBinder，用于参数解析器ArgumentResolver中
    @Nullable
    private WebDataBinderFactory dataBinderFactory;


    /**
     * Create an instance from a {@code HandlerMethod}.
     */
    public InvocableHandlerMethod(HandlerMethod handlerMethod) {
        super(handlerMethod);
    }

    /**
     * Create an instance from a bean instance and a method.
     */
    public InvocableHandlerMethod(Object bean, Method method) {
        super(bean, method);
    }

    /**
     * Construct a new handler method with the given bean instance, method name and parameters.
     * @param bean the object bean
     * @param methodName the method name
     * @param parameterTypes the method parameter types
     * @throws NoSuchMethodException when the method cannot be found
     */
    public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {

        super(bean, methodName, parameterTypes);
    }


    /**
     * Set {@link HandlerMethodArgumentResolver}s to use to use for resolving method argument values.
     */
    public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
        this.resolvers = argumentResolvers;
    }

    /**
     * Set the ParameterNameDiscoverer for resolving parameter names when needed
     * (e.g. default request attribute name).
     * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
     */
    public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    /**
     * Set the {@link WebDataBinderFactory} to be passed to argument resolvers allowing them
     * to create a {@link WebDataBinder} for data binding and type conversion purposes.
     */
    public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
        this.dataBinderFactory = dataBinderFactory;
    }


    /**
     * Invoke the method after resolving its argument values in the context of the given request.
     * <p>Argument values are commonly resolved through {@link HandlerMethodArgumentResolver}s.
     * The {@code providedArgs} parameter however may supply argument values to be used directly,
     * i.e. without argument resolution. Examples of provided argument values include a
     * {@link WebDataBinder}, a {@link SessionStatus}, or a thrown exception instance.
     * Provided argument values are checked before argument resolvers.
     * @param request the current request
     * @param mavContainer the ModelAndViewContainer for this request
     * @param providedArgs "given" arguments matched by type, not resolved
     * @return the raw value returned by the invoked method
     * @throws Exception raised if no suitable argument resolver can be found,
     * or if the method raised an exception
     */
    @Nullable
    public Object invokeForRequest(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
                                   Object... providedArgs) throws Exception {

        // 准备方法所需的参数
        Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
        if (logger.isTraceEnabled()) {
            logger.trace("Invoking '" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
                    "' with arguments " + Arrays.toString(args));
        }
        // 具体调用Method
        Object returnValue = doInvoke(args);
        if (logger.isTraceEnabled()) {
            logger.trace("Method [" + ClassUtils.getQualifiedMethodName(getMethod(), getBeanType()) +
                    "] returned [" + returnValue + "]");
        }
        return returnValue;
    }

    /**
     * Get the method argument values for the current request.
     */
    private Object[] getMethodArgumentValues(NativeWebRequest request, @Nullable ModelAndViewContainer mavContainer,
                                             Object... providedArgs) throws Exception {

        // 获取方法的参数，在HandlerMethod中
        MethodParameter[] parameters = getMethodParameters();
        // 用于保存解析出参数的值
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            MethodParameter parameter = parameters[i];
            // 给parameter设置参数名解析器
            parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
            // 如果相应类型的参数已经在providedArgs中提供了，则直接设置到parameter
            args[i] = resolveProvidedArgument(parameter, providedArgs);
            if (args[i] != null) {
                continue;
            }
            // 使用argumentResolvers解析参数
            if (this.resolvers.supportsParameter(parameter)) {
                try {
                    args[i] = this.resolvers.resolveArgument(
                            parameter, mavContainer, request, this.dataBinderFactory);
                    continue;
                } catch (Exception ex) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(getArgumentResolutionErrorMessage("Failed to resolve", i), ex);
                    }
                    throw ex;
                }
            }
            // 如果没解析出参数，则抛出异常
            if (args[i] == null) {
                throw new IllegalStateException("Could not resolve method parameter at index " +
                        parameter.getParameterIndex() + " in " + parameter.getExecutable().toGenericString() +
                        ": " + getArgumentResolutionErrorMessage("No suitable resolver for", i));
            }
        }
        return args;
    }

    private String getArgumentResolutionErrorMessage(String text, int index) {
        Class<?> paramType = getMethodParameters()[index].getParameterType();
        return text + " argument " + index + " of type '" + paramType.getName() + "'";
    }

    /**
     * Attempt to resolve a method parameter from the list of provided argument values.
     */
    @Nullable
    private Object resolveProvidedArgument(MethodParameter parameter, @Nullable Object... providedArgs) {
        if (providedArgs == null) {
            return null;
        }
        for (Object providedArg : providedArgs) {
            if (parameter.getParameterType().isInstance(providedArg)) {
                return providedArg;
            }
        }
        return null;
    }


    /**
     * Invoke the handler method with the given argument values.
     */
    protected Object doInvoke(Object... args) throws Exception {
        // 强制转变桥接方法为可调用，即使是private也可以被调用
        ReflectionUtils.makeAccessible(getBridgedMethod());
        try {
            return getBridgedMethod().invoke(getBean(), args);
        } catch (IllegalArgumentException ex) {
            assertTargetBean(getBridgedMethod(), getBean(), args);
            String text = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
            throw new IllegalStateException(getInvocationErrorMessage(text, args), ex);
        } catch (InvocationTargetException ex) {
            // Unwrap for HandlerExceptionResolvers ...
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            } else if (targetException instanceof Error) {
                throw (Error) targetException;
            } else if (targetException instanceof Exception) {
                throw (Exception) targetException;
            } else {
                String text = getInvocationErrorMessage("Failed to invoke handler method", args);
                throw new IllegalStateException(text, targetException);
            }
        }
    }

    /**
     * Assert that the target bean class is an instance of the class where the given
     * method is declared. In some cases the actual controller instance at request-
     * processing time may be a JDK dynamic proxy (lazy initialization, prototype
     * beans, and others). {@code @Controller}'s that require proxying should prefer
     * class-based proxy mechanisms.
     */
    private void assertTargetBean(Method method, Object targetBean, Object[] args) {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        Class<?> targetBeanClass = targetBean.getClass();
        if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
            String text = "The mapped handler method class '" + methodDeclaringClass.getName() +
                    "' is not an instance of the actual controller bean class '" +
                    targetBeanClass.getName() + "'. If the controller requires proxying " +
                    "(e.g. due to @Transactional), please use class-based proxying.";
            throw new IllegalStateException(getInvocationErrorMessage(text, args));
        }
    }

    private String getInvocationErrorMessage(String text, Object[] resolvedArgs) {
        StringBuilder sb = new StringBuilder(getDetailedErrorMessage(text));
        sb.append("Resolved arguments: \n");
        for (int i = 0; i < resolvedArgs.length; i++) {
            sb.append("[").append(i).append("] ");
            if (resolvedArgs[i] == null) {
                sb.append("[null] \n");
            } else {
                sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
                sb.append("[value=").append(resolvedArgs[i]).append("]\n");
            }
        }
        return sb.toString();
    }

    /**
     * Adds HandlerMethod details such as the bean type and method signature to the message.
     * @param text error message to append the HandlerMethod details to
     */
    protected String getDetailedErrorMessage(String text) {
        StringBuilder sb = new StringBuilder(text).append("\n");
        sb.append("HandlerMethod details: \n");
        sb.append("Controller [").append(getBeanType().getName()).append("]\n");
        sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
        return sb.toString();
    }

}
