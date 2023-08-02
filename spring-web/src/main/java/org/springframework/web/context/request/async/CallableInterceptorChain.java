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

package org.springframework.web.context.request.async;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Assists with the invocation of {@link CallableProcessingInterceptor}'s.
 *
 * @author Rossen Stoyanchev
 * @author Rob Winch
 * @since 3.2
 */
class CallableInterceptorChain {

	private static final Log logger = LogFactory.getLog(CallableInterceptorChain.class);

	private final List<CallableProcessingInterceptor> interceptors;

	private int preProcessIndex = -1;

	private volatile Future<?> taskFuture;


	public CallableInterceptorChain(List<CallableProcessingInterceptor> interceptors) {
		this.interceptors = interceptors;
	}


	public void setTaskFuture(Future<?> taskFuture) {
		this.taskFuture = taskFuture;
	}


    // 对应Interceptor中的beforeConcurrentHandling方法
    public void applyBeforeConcurrentHandling(NativeWebRequest request, Callable<?> task) throws Exception {
        for (CallableProcessingInterceptor interceptor : this.interceptors) {
            interceptor.beforeConcurrentHandling(request, task);
        }
    }

    // 对应Interceptor中的preProcess方法
    public void applyPreProcess(NativeWebRequest request, Callable<?> task) throws Exception {
        for (CallableProcessingInterceptor interceptor : this.interceptors) {
            interceptor.preProcess(request, task);
            this.preProcessIndex++;
        }
    }

    // 对应Interceptor中的postProcess方法
    public Object applyPostProcess(NativeWebRequest request, Callable<?> task, Object concurrentResult) {
        Throwable exceptionResult = null;
        for (int i = this.preProcessIndex; i >= 0; i--) {
            try {
                this.interceptors.get(i).postProcess(request, task, concurrentResult);
            } catch (Throwable t) {
                // Save the first exception but invoke all interceptors
                if (exceptionResult != null) {
                    logger.error("postProcess error", t);
                } else {
                    exceptionResult = t;
                }
            }
        }
        return (exceptionResult != null) ? exceptionResult : concurrentResult;
    }

    // 对应Interceptor中的afterTimeout方法
    public Object triggerAfterTimeout(NativeWebRequest request, Callable<?> task) {
        cancelTask();
        for (CallableProcessingInterceptor interceptor : this.interceptors) {
            try {
                Object result = interceptor.handleTimeout(request, task);
                if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
                    break;
                } else if (result != CallableProcessingInterceptor.RESULT_NONE) {
                    return result;
                }
            } catch (Throwable t) {
                return t;
            }
        }
        return CallableProcessingInterceptor.RESULT_NONE;
    }

	private void cancelTask() {
		Future<?> future = this.taskFuture;
		if (future != null) {
			try {
				future.cancel(true);
			}
			catch (Throwable ex) {
				// Ignore
			}
		}
	}

	public Object triggerAfterError(NativeWebRequest request, Callable<?> task, Throwable throwable) {
		cancelTask();
		for (CallableProcessingInterceptor interceptor : this.interceptors) {
			try {
				Object result = interceptor.handleError(request, task, throwable);
				if (result == CallableProcessingInterceptor.RESPONSE_HANDLED) {
					break;
				}
				else if (result != CallableProcessingInterceptor.RESULT_NONE) {
					return result;
				}
			}
			catch (Throwable t) {
				return t;
			}
		}
		return CallableProcessingInterceptor.RESULT_NONE;
	}

    // 对应Interceptor中的afterCompletion方法
    public void triggerAfterCompletion(NativeWebRequest request, Callable<?> task) {
        for (int i = this.interceptors.size()-1; i >= 0; i--) {
            try {
                this.interceptors.get(i).afterCompletion(request, task);
            } catch (Throwable t) {
                logger.error("afterCompletion error", t);
            }
        }
    }

}
