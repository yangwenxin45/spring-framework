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

package org.springframework.web.method.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.Conventions;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpSessionRequiredException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Assist with initialization of the {@link Model} before controller method
 * invocation and with updates to it after the invocation.
 *
 * <p>On initialization the model is populated with attributes temporarily stored
 * in the session and through the invocation of {@code @ModelAttribute} methods.
 *
 * <p>On update model attributes are synchronized with the session and also
 * {@link org.springframework.validation.BindingResult} attributes are added if missing.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */

/**
 * ModelFactory是用来维护Model的，具体包含两个功能：
 * 1. 初始化Model
 * 2. 处理器执行后将Model中相应的参数更新到SessionAttributes中
 *
 * @author yangwenxin
 * @date 2023-07-26 16:57
 */
public final class ModelFactory {

    private static final Log logger = LogFactory.getLog(ModelFactory.class);

    private final List<ModelMethod> modelMethods = new ArrayList<>();

    private final WebDataBinderFactory dataBinderFactory;

    private final SessionAttributesHandler sessionAttributesHandler;


    /**
     * Create a new instance with the given {@code @ModelAttribute} methods.
     * @param handlerMethods the {@code @ModelAttribute} methods to invoke
     * @param binderFactory for preparation of {@link BindingResult} attributes
     * @param attributeHandler for access to session attributes
     */
    public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
                        WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

        if (handlerMethods != null) {
            for (InvocableHandlerMethod handlerMethod : handlerMethods) {
                this.modelMethods.add(new ModelMethod(handlerMethod));
            }
        }
        this.dataBinderFactory = binderFactory;
        this.sessionAttributesHandler = attributeHandler;
    }


    /**
     * Populate the model in the following order:
     * <ol>
     * <li>Retrieve "known" session attributes listed as {@code @SessionAttributes}.
     * <li>Invoke {@code @ModelAttribute} methods
     * <li>Find {@code @ModelAttribute} method arguments also listed as
     * {@code @SessionAttributes} and ensure they're present in the model raising
     * an exception if necessary.
     * </ol>
     * @param request the current request
     * @param container a container with the model to be initialized
     * @param handlerMethod the method for which the model is initialized
     * @throws Exception may arise from {@code @ModelAttribute} methods
     */
    /**
     * Model中参数的优先级：
     * 1. FlashMap中保存的参数优先级最高，它在ModelFactory前面执行
     * 2. SessionAttributes中保存的参数的优先级第二，它不可以覆盖FlashMap中设置的参数
     * 3. 通过注解了@ModelAttribute的方法设置的参数优先级第三
     * 4. 注解了@ModelAttribute而且从别的处理器的SessionAttributes中获取的参数优先级最低
     *
     * @author yangwenxin
     * @date 2023-07-27 09:42
     */
    public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
            throws Exception {

        // 从SessionAttributes中取出保存的参数，并合并到mavContainer中
        Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
        container.mergeAttributes(sessionAttributes);
        // 执行注解了@ModelAttribute的方法并将结果设置到Model
        invokeModelAttributeMethods(request, container);

        // 遍历既注释了@ModelAttribute又在@SessionAttributes注释中的参数是否已经设置到mavContainer中
        for (String name : findSessionAttributeArguments(handlerMethod)) {
            if (!container.containsAttribute(name)) {
                Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
                if (value == null) {
                    throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
                }
                container.addAttribute(name, value);
            }
        }
    }

    /**
     * Invoke model attribute methods to populate the model.
     * Attributes are added only if not already present in the model.
     */
    private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
            throws Exception {

        while (!this.modelMethods.isEmpty()) {
            // 获取注解了@ModelAttribute的方法
            InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
            // 获取注解@ModelAttribute中设置的value作为参数名
            ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
            Assert.state(ann != null, "No ModelAttribute annotation");
            // 如果参数名已经在mavContainer中则跳过
            if (container.containsAttribute(ann.name())) {
                if (!ann.binding()) {
                    container.setBindingDisabled(ann.name());
                }
                continue;
            }

            // 执行@ModelAttribute注解的方法
            Object returnValue = modelMethod.invokeForRequest(request, container);
            if (!modelMethod.isVoid()) {
                // 使用getNameForReturnValue获取参数名
                String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
                if (!ann.binding()) {
                    container.setBindingDisabled(returnValueName);
                }
                if (!container.containsAttribute(returnValueName)) {
                    container.addAttribute(returnValueName, returnValue);
                }
            }
        }
    }

    private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
        for (ModelMethod modelMethod : this.modelMethods) {
            if (modelMethod.checkDependencies(container)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Selected @ModelAttribute method " + modelMethod);
                }
                this.modelMethods.remove(modelMethod);
                return modelMethod;
            }
        }
        ModelMethod modelMethod = this.modelMethods.get(0);
        if (logger.isTraceEnabled()) {
            logger.trace("Selected @ModelAttribute method (not present: " +
                    modelMethod.getUnresolvedDependencies(container)+ ") " + modelMethod);
        }
        this.modelMethods.remove(modelMethod);
        return modelMethod;
    }

    /**
     * Find {@code @ModelAttribute} arguments also listed as {@code @SessionAttributes}.
     */
    private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
        List<String> result = new ArrayList<>();
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                String name = getNameForParameter(parameter);
                Class<?> paramType = parameter.getParameterType();
                if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /**
     * Promote model attributes listed as {@code @SessionAttributes} to the session.
     * Add {@link BindingResult} attributes where necessary.
     * @param request the current request
     * @param container contains the model to update
     * @throws Exception if creating BindingResult attributes fails
     */
    /**
     * updateModel主要做了两件事：
     * 1. 维护SessionAttributes的数据
     * 2. 给Model中需要的参数设置BindingResult，以备视图使用
     *
     * @author yangwenxin
     * @date 2023-07-27 09:57
     */
    public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
        ModelMap defaultModel = container.getDefaultModel();
        // 处理器是否调用了SessionStatus.setComplete方法将SessionAttributes清空
        if (container.getSessionStatus().isComplete()) {
            // 清空SessionAttributes
            this.sessionAttributesHandler.cleanupAttributes(request);
        } else {
            // 将mavContainer的defaultModel中相应的参数设置到SessionAttributes中
            this.sessionAttributesHandler.storeAttributes(request, defaultModel);
        }
        // 判断请求是否应处理完或者是redirect类型的返回值，也就是不需要渲染页面
        if (!container.isRequestHandled() && container.getModel() == defaultModel) {
            // 需要渲染则给Model中相应参数设置BindingResult
            updateBindingResult(request, defaultModel);
        }
    }

    /**
     * Add {@link BindingResult} attributes to the model for attributes that require it.
     */
    private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
        List<String> keyNames = new ArrayList<>(model.keySet());
        for (String name : keyNames) {
            Object value = model.get(name);
            if (value != null && isBindingCandidate(name, value)) {
                String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
                if (!model.containsAttribute(bindingResultKey)) {
                    WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
                    model.put(bindingResultKey, dataBinder.getBindingResult());
                }
            }
        }
    }

    /**
     * Whether the given attribute requires a {@link BindingResult} in the model.
     */
    private boolean isBindingCandidate(String attributeName, Object value) {
        // 判断是不是其他参数绑定结果的BindingResult
        if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
            return false;
        }

        // 判断是不是SessionAttributes管理的属性
        if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
            return true;
        }

        // 判断是不是空值、数组、Collection、Map和简单类型
        return (!value.getClass().isArray() && !(value instanceof Collection) &&
                !(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
    }


    /**
     * Derive the model attribute name for the given method parameter based on
     * a {@code @ModelAttribute} parameter annotation (if present) or falling
     * back on parameter type based conventions.
     * @param parameter a descriptor for the method parameter
     * @return the derived name
     * @see Conventions#getVariableNameForParameter(MethodParameter)
     */
    public static String getNameForParameter(MethodParameter parameter) {
        ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
        String name = (ann != null ? ann.value() : null);
        return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
    }

    /**
     * Derive the model attribute name for the given return value based on:
     * <ol>
     * <li>the method {@code ModelAttribute} annotation value
     * <li>the declared return type if it is more specific than {@code Object}
     * <li>the actual return value type
     * </ol>
     * @param returnValue the value returned from a method invocation
     * @param returnType a descriptor for the return type of the method
     * @return the derived name (never {@code null} or empty String)
     */
    public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
        // 获取返回值的@ModelAttribute注解，也就是方法的@ModelAttribute注解
        ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
        // 如果设置了value则直接将其作为参数名返回
        if (ann != null && StringUtils.hasText(ann.value())) {
            return ann.value();
        } else {
            Method method = returnType.getMethod();
            Assert.state(method != null, "No handler method");
            Class<?> containingClass = returnType.getContainingClass();
            Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
            // 使用getVariableNameForReturnType根据方法、返回值类型和返回值获取参数名
            return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
        }
    }


    private static class ModelMethod {

        private final InvocableHandlerMethod handlerMethod;

        private final Set<String> dependencies = new HashSet<>();

        public ModelMethod(InvocableHandlerMethod handlerMethod) {
            this.handlerMethod = handlerMethod;
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                    this.dependencies.add(getNameForParameter(parameter));
                }
            }
        }

        public InvocableHandlerMethod getHandlerMethod() {
            return this.handlerMethod;
        }

        public boolean checkDependencies(ModelAndViewContainer mavContainer) {
            for (String name : this.dependencies) {
                if (!mavContainer.containsAttribute(name)) {
                    return false;
                }
            }
            return true;
        }

        public List<String> getUnresolvedDependencies(ModelAndViewContainer mavContainer) {
            List<String> result = new ArrayList<>(this.dependencies.size());
            for (String name : this.dependencies) {
                if (!mavContainer.containsAttribute(name)) {
                    result.add(name);
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return this.handlerMethod.getMethod().toGenericString();
        }
    }

}
