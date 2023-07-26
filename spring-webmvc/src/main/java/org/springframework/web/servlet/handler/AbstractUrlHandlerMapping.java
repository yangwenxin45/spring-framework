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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerExecutionChain;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * Abstract base class for URL-mapped {@link org.springframework.web.servlet.HandlerMapping}
 * implementations. Provides infrastructure for mapping handlers to URLs and configurable
 * URL lookup. For information on the latter, see "alwaysUseFullPath" property.
 *
 * <p>Supports direct matches, e.g. a registered "/test" matches "/test", and
 * various Ant-style pattern matches, e.g. a registered "/t*" pattern matches
 * both "/test" and "/team", "/test/*" matches all paths in the "/test" directory,
 * "/test/**" matches all paths below "/test". For details, see the
 * {@link org.springframework.util.AntPathMatcher AntPathMatcher} javadoc.
 *
 * <p>Will search all path patterns to find the most exact match for the
 * current request path. The most exact match is defined as the longest
 * path pattern that matches the current request path.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @since 16.04.2003
 */

/**
 * 大致原理：
 * 首先将url与对应的Handler保存在一个Map中，在getHandlerInternal方法中使用url从Map中获取Handler，
 * AbstractUrlHandlerMapping中实现了具体用url从Map中获取Handler的过程，而Map的初始化则交给了具体的子孙类去完成
 *
 * @author yangwenxin
 * @date 2023-07-25 14:05
 */
public abstract class AbstractUrlHandlerMapping extends AbstractHandlerMapping implements MatchableHandlerMapping {

    // 处理"/"请求的处理器
    @Nullable
    private Object rootHandler;

    private boolean useTrailingSlashMatch = false;

    private boolean lazyInitHandlers = false;

    // 保存url和Handler的映射Map
    // 两种初始化方式：一种是手工在配置文件里注册，另一种是在spring的容器里找
    private final Map<String, Object> handlerMap = new LinkedHashMap<>();


    /**
     * Set the root handler for this handler mapping, that is,
     * the handler to be registered for the root path ("/").
     * <p>Default is {@code null}, indicating no root handler.
     */
    public void setRootHandler(@Nullable Object rootHandler) {
        this.rootHandler = rootHandler;
    }

    /**
     * Return the root handler for this handler mapping (registered for "/"),
     * or {@code null} if none.
     */
    @Nullable
    public Object getRootHandler() {
        return this.rootHandler;
    }

    /**
     * Whether to match to URLs irrespective of the presence of a trailing slash.
     * If enabled a URL pattern such as "/users" also matches to "/users/".
     * <p>The default value is {@code false}.
     */
    public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
        this.useTrailingSlashMatch = useTrailingSlashMatch;
    }

    /**
     * Whether to match to URLs irrespective of the presence of a trailing slash.
     */
    public boolean useTrailingSlashMatch() {
        return this.useTrailingSlashMatch;
    }

    /**
     * Set whether to lazily initialize handlers. Only applicable to
     * singleton handlers, as prototypes are always lazily initialized.
     * Default is "false", as eager initialization allows for more efficiency
     * through referencing the controller objects directly.
     * <p>If you want to allow your controllers to be lazily initialized,
     * make them "lazy-init" and set this flag to true. Just making them
     * "lazy-init" will not work, as they are initialized through the
     * references from the handler mapping in this case.
     */
    public void setLazyInitHandlers(boolean lazyInitHandlers) {
        this.lazyInitHandlers = lazyInitHandlers;
    }

    /**
     * Look up a handler for the URL path of the given request.
     * @param request current HTTP request
     * @return the handler instance, or {@code null} if none found
     */
    @Override
    @Nullable
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        // 截取用于匹配的url有效路径
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        // 根据路径寻找handler
        Object handler = lookupHandler(lookupPath, request);
        if (handler == null) {
            // We need to care for the default handler directly, since we need to
            // expose the PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE for it as well.
            // 定义一个临时变量，保存找到的原始Handler
            Object rawHandler = null;
            if ("/".equals(lookupPath)) {
                // 如果请求的路径仅仅是"/"，那么使用RootHandler进行处理
                rawHandler = getRootHandler();
            }
            if (rawHandler == null) {
                // 无法找到handler则使用默认handler
                rawHandler = getDefaultHandler();
            }
            if (rawHandler != null) {
                // Bean name or resolved handler?
                // 如果是String类型则到容器中查找具体的Bean
                if (rawHandler instanceof String) {
                    String handlerName = (String) rawHandler;
                    rawHandler = obtainApplicationContext().getBean(handlerName);
                }
                // 模板方法，用来校验找到的Handler和request是否匹配
                validateHandler(rawHandler, request);
                handler = buildPathExposingHandler(rawHandler, lookupPath, lookupPath, null);
            }
        }
        if (handler != null && logger.isDebugEnabled()) {
            logger.debug("Mapping [" + lookupPath + "] to " + handler);
        } else if (handler == null && logger.isTraceEnabled()) {
            logger.trace("No handler mapping found for [" + lookupPath + "]");
        }
        return handler;
    }

    /**
     * Look up a handler instance for the given URL path.
     * <p>Supports direct matches, e.g. a registered "/test" matches "/test",
     * and various Ant-style pattern matches, e.g. a registered "/t*" matches
     * both "/test" and "/team". For details, see the AntPathMatcher class.
     * <p>Looks for the most exact pattern, where most exact is defined as
     * the longest path pattern.
     * @param urlPath the URL the bean is mapped to
     * @param request current HTTP request (to expose the path within the mapping to)
     * @return the associated handler instance, or {@code null} if not found
     * @see #exposePathWithinMapping
     * @see org.springframework.util.AntPathMatcher
     */
    @Nullable
    protected Object lookupHandler(String urlPath, HttpServletRequest request) throws Exception {
        // Direct match?
        // 直接从Map中获取
        Object handler = this.handlerMap.get(urlPath);
        if (handler != null) {
            // Bean name or resolved handler?
            if (handler instanceof String) {
                String handlerName = (String) handler;
                handler = obtainApplicationContext().getBean(handlerName);
            }
            validateHandler(handler, request);
            return buildPathExposingHandler(handler, urlPath, urlPath, null);
        }

        // Pattern match?
        // 通配符匹配的处理
        List<String> matchingPatterns = new ArrayList<>();
        for (String registeredPattern : this.handlerMap.keySet()) {
            if (getPathMatcher().match(registeredPattern, urlPath)) {
                matchingPatterns.add(registeredPattern);
            } else if (useTrailingSlashMatch()) {
                if (!registeredPattern.endsWith("/") && getPathMatcher().match(registeredPattern + "/", urlPath)) {
                    matchingPatterns.add(registeredPattern + "/");
                }
            }
        }

        String bestMatch = null;
        Comparator<String> patternComparator = getPathMatcher().getPatternComparator(urlPath);
        if (!matchingPatterns.isEmpty()) {
            matchingPatterns.sort(patternComparator);
            if (logger.isDebugEnabled()) {
                logger.debug("Matching patterns for request [" + urlPath + "] are " + matchingPatterns);
            }
            bestMatch = matchingPatterns.get(0);
        }
        if (bestMatch != null) {
            handler = this.handlerMap.get(bestMatch);
            if (handler == null) {
                if (bestMatch.endsWith("/")) {
                    handler = this.handlerMap.get(bestMatch.substring(0, bestMatch.length() - 1));
                }
                if (handler == null) {
                    throw new IllegalStateException(
                            "Could not find handler for best pattern match [" + bestMatch + "]");
                }
            }
            // Bean name or resolved handler?
            // 如果是String类型则从容器中获取
            if (handler instanceof String) {
                String handlerName = (String) handler;
                handler = obtainApplicationContext().getBean(handlerName);
            }
            validateHandler(handler, request);
            String pathWithinMapping = getPathMatcher().extractPathWithinPattern(bestMatch, urlPath);

            // There might be multiple 'best patterns', let's make sure we have the correct URI template variables
            // for all of them
            // 之前是通过sort方法进行排序，然后拿第一个作为bestPatternMatch的、不过有可能有多个Pattern的顺序相同，也就是sort方法返回0，这里就是处理这种情况
            Map<String, String> uriTemplateVariables = new LinkedHashMap<>();
            for (String matchingPattern : matchingPatterns) {
                if (patternComparator.compare(bestMatch, matchingPattern) == 0) {
                    Map<String, String> vars = getPathMatcher().extractUriTemplateVariables(matchingPattern, urlPath);
                    Map<String, String> decodedVars = getUrlPathHelper().decodePathVariables(request, vars);
                    uriTemplateVariables.putAll(decodedVars);
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("URI Template variables for request [" + urlPath + "] are " + uriTemplateVariables);
            }
            return buildPathExposingHandler(handler, bestMatch, pathWithinMapping, uriTemplateVariables);
        }

        // No handler found...
        // 没找到Handler则返回null
        return null;
    }

    /**
     * Validate the given handler against the current request.
     * <p>The default implementation is empty. Can be overridden in subclasses,
     * for example to enforce specific preconditions expressed in URL mappings.
     * @param handler the handler object to validate
     * @param request current HTTP request
     * @throws Exception if validation failed
     */
    protected void validateHandler(Object handler, HttpServletRequest request) throws Exception {
    }

    /**
     * Build a handler object for the given raw handler, exposing the actual
     * handler, the {@link #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE}, as well as
     * the {@link #URI_TEMPLATE_VARIABLES_ATTRIBUTE} before executing the handler.
     * <p>The default implementation builds a {@link HandlerExecutionChain}
     * with a special interceptor that exposes the path attribute and uri template variables
     * @param rawHandler the raw handler to expose
     * @param pathWithinMapping the path to expose before executing the handler
     * @param uriTemplateVariables the URI template variables, can be {@code null} if no variables found
     * @return the final handler object
     */
    /**
     * 用于给查找到的Handler注册两个拦截器PathExposingHandlerInterceptor和UriTemplateVariablesHandlerInterceptor，
     * 这是两个内部拦截器，主要作用是将与当前url实际匹配的Pattern、匹配条件和url模板参数设置到request的属性里，
     * 这样在后面的处理过程中就可以直接从request属性中获取，而不需要在重新查找一遍了
     *
     * @author yangwenxin
     * @date 2023-07-25 14:26
     */
    protected Object buildPathExposingHandler(Object rawHandler, String bestMatchingPattern,
                                              String pathWithinMapping, @Nullable Map<String, String> uriTemplateVariables) {

        HandlerExecutionChain chain = new HandlerExecutionChain(rawHandler);
        chain.addInterceptor(new PathExposingHandlerInterceptor(bestMatchingPattern, pathWithinMapping));
        if (!CollectionUtils.isEmpty(uriTemplateVariables)) {
            chain.addInterceptor(new UriTemplateVariablesHandlerInterceptor(uriTemplateVariables));
        }
        return chain;
    }

    /**
     * Expose the path within the current mapping as request attribute.
     * @param pathWithinMapping the path within the current mapping
     * @param request the request to expose the path to
     * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
     */
    protected void exposePathWithinMapping(String bestMatchingPattern, String pathWithinMapping,
                                           HttpServletRequest request) {

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestMatchingPattern);
        request.setAttribute(PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
    }

    /**
     * Expose the URI templates variables as request attribute.
     * @param uriTemplateVariables the URI template variables
     * @param request the request to expose the path to
     * @see #PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
     */
    protected void exposeUriTemplateVariables(Map<String, String> uriTemplateVariables, HttpServletRequest request) {
        request.setAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriTemplateVariables);
    }

    @Override
    @Nullable
    public RequestMatchResult match(HttpServletRequest request, String pattern) {
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        if (getPathMatcher().match(pattern, lookupPath)) {
            return new RequestMatchResult(pattern, lookupPath, getPathMatcher());
        } else if (useTrailingSlashMatch()) {
            if (!pattern.endsWith("/") && getPathMatcher().match(pattern + "/", lookupPath)) {
                return new RequestMatchResult(pattern + "/", lookupPath, getPathMatcher());
            }
        }
        return null;
    }

    /**
     * Register the specified handler for the given URL paths.
     * @param urlPaths the URLs that the bean should be mapped to
     * @param beanName the name of the handler bean
     * @throws BeansException if the handler couldn't be registered
     * @throws IllegalStateException if there is a conflicting handler registered
     */
    protected void registerHandler(String[] urlPaths, String beanName) throws BeansException, IllegalStateException {
        Assert.notNull(urlPaths, "URL path array must not be null");
        for (String urlPath : urlPaths) {
            registerHandler(urlPath, beanName);
        }
    }

    /**
     * Register the specified handler for the given URL path.
     * @param urlPath the URL the bean should be mapped to
     * @param handler the handler instance or handler bean name String
     * (a bean name will automatically be resolved into the corresponding handler bean)
     * @throws BeansException if the handler couldn't be registered
     * @throws IllegalStateException if there is a conflicting handler registered
     */
    protected void registerHandler(String urlPath, Object handler) throws BeansException, IllegalStateException {
        Assert.notNull(urlPath, "URL path must not be null");
        Assert.notNull(handler, "Handler object must not be null");
        Object resolvedHandler = handler;

        // Eagerly resolve handler if referencing singleton via name.
        // 如果Handler是String类型而且没有设置lazyInitHandlers则从SpringMVC容器中获取Handler
        if (!this.lazyInitHandlers && handler instanceof String) {
            String handlerName = (String) handler;
            ApplicationContext applicationContext = obtainApplicationContext();
            if (applicationContext.isSingleton(handlerName)) {
                resolvedHandler = applicationContext.getBean(handlerName);
            }
        }

        /**
         * 注册Handler到Map里的过程：
         * 首先看Map里原来有没有传入的url，如果没有就put进去，如果有就看一下原来保存的和现在要注册的Handler是不是同一个，
         * 如果不是同一个就有问题了，总不能相同的url有两个不同的Handler吧，这是就得抛异常。
         * 往Map里放的时候还需要看一下url是不是处理"/"或者"/*"，如果是就不往Map里放了，而是分别设置到rootHandler和defaultHandler
         *
         * @author yangwenxin
         * @date 2023-07-25 14:38
         */
        Object mappedHandler = this.handlerMap.get(urlPath);
        if (mappedHandler != null) {
            if (mappedHandler != resolvedHandler) {
                throw new IllegalStateException(
                        "Cannot map " + getHandlerDescription(handler) + " to URL path [" + urlPath +
                                "]: There is already " + getHandlerDescription(mappedHandler) + " mapped.");
            }
        } else {
            if (urlPath.equals("/")) {
                if (logger.isInfoEnabled()) {
                    logger.info("Root mapping to " + getHandlerDescription(handler));
                }
                setRootHandler(resolvedHandler);
            } else if (urlPath.equals("/*")) {
                if (logger.isInfoEnabled()) {
                    logger.info("Default mapping to " + getHandlerDescription(handler));
                }
                setDefaultHandler(resolvedHandler);
            } else {
                this.handlerMap.put(urlPath, resolvedHandler);
                if (logger.isInfoEnabled()) {
                    logger.info("Mapped URL path [" + urlPath + "] onto " + getHandlerDescription(handler));
                }
            }
        }
    }

    private String getHandlerDescription(Object handler) {
        return "handler " + (handler instanceof String ? "'" + handler + "'" : "of type [" + handler.getClass() + "]");
    }


    /**
     * Return the registered handlers as an unmodifiable Map, with the registered path
     * as key and the handler object (or handler bean name in case of a lazy-init handler)
     * as value.
     * @see #getDefaultHandler()
     */
    public final Map<String, Object> getHandlerMap() {
        return Collections.unmodifiableMap(this.handlerMap);
    }

    /**
     * Indicates whether this handler mapping support type-level mappings. Default to {@code false}.
     */
    protected boolean supportsTypeLevelMappings() {
        return false;
    }


    /**
     * Special interceptor for exposing the
     * {@link AbstractUrlHandlerMapping#PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE} attribute.
     * @see AbstractUrlHandlerMapping#exposePathWithinMapping
     */
    private class PathExposingHandlerInterceptor extends HandlerInterceptorAdapter {

        private final String bestMatchingPattern;

        private final String pathWithinMapping;

        public PathExposingHandlerInterceptor(String bestMatchingPattern, String pathWithinMapping) {
            this.bestMatchingPattern = bestMatchingPattern;
            this.pathWithinMapping = pathWithinMapping;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            exposePathWithinMapping(this.bestMatchingPattern, this.pathWithinMapping, request);
            request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
            request.setAttribute(INTROSPECT_TYPE_LEVEL_MAPPING, supportsTypeLevelMappings());
            return true;
        }

    }

    /**
     * Special interceptor for exposing the
     * {@link AbstractUrlHandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} attribute.
     * @see AbstractUrlHandlerMapping#exposePathWithinMapping
     */
    private class UriTemplateVariablesHandlerInterceptor extends HandlerInterceptorAdapter {

        private final Map<String, String> uriTemplateVariables;

        public UriTemplateVariablesHandlerInterceptor(Map<String, String> uriTemplateVariables) {
            this.uriTemplateVariables = uriTemplateVariables;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            exposeUriTemplateVariables(this.uriTemplateVariables, request);
            return true;
        }
    }

}
