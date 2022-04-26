/*
 * Copyright 2002-2022 the original author or authors.
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

package org.springframework.web.service.invoker;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.service.annotation.HttpExchange;


/**
 * Factory to create a proxy for an HTTP service with {@link HttpExchange} methods.
 *
 * @author Rossen Stoyanchev
 * @since 6.0
 */
public class HttpServiceProxyFactory {

	private final List<HttpServiceArgumentResolver> argumentResolvers;

	private final HttpClientAdapter clientAdapter;

	private final ReactiveAdapterRegistry reactiveAdapterRegistry;

	private final Duration blockTimeout;


	public HttpServiceProxyFactory(
			List<HttpServiceArgumentResolver> argumentResolvers, HttpClientAdapter clientAdapter,
			ReactiveAdapterRegistry reactiveAdapterRegistry, Duration blockTimeout) {

		this.argumentResolvers = argumentResolvers;
		this.clientAdapter = clientAdapter;
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
		this.blockTimeout = blockTimeout;
	}


	/**
	 * Create a proxy for executing requests to the given HTTP service.
	 * @param serviceType the HTTP service to create a proxy for
	 * @param <S> the service type
	 * @return the created proxy
	 */
	public <S> S createClient(Class<S> serviceType) {

		List<HttpServiceMethod> methods =
				MethodIntrospector.selectMethods(serviceType, this::isHttpRequestMethod)
						.stream()
						.map(method -> initServiceMethod(method, serviceType))
						.toList();

		return ProxyFactory.getProxy(serviceType, new HttpServiceMethodInterceptor(methods));
	}

	private boolean isHttpRequestMethod(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, HttpExchange.class);
	}

	private HttpServiceMethod initServiceMethod(Method method, Class<?> serviceType) {
		return new HttpServiceMethod(
				method, serviceType, this.argumentResolvers,
				this.clientAdapter, this.reactiveAdapterRegistry, this.blockTimeout);
	}


	/**
	 * {@link MethodInterceptor} that invokes an {@link HttpServiceMethod}.
	 */
	private static final class HttpServiceMethodInterceptor implements MethodInterceptor {

		private final Map<Method, HttpServiceMethod> httpServiceMethods = new HashMap<>();

		private HttpServiceMethodInterceptor(List<HttpServiceMethod> methods) {
			methods.forEach(serviceMethod -> this.httpServiceMethods.put(serviceMethod.getMethod(), serviceMethod));
		}

		@Override
		public Object invoke(MethodInvocation invocation) {
			Method method = invocation.getMethod();
			HttpServiceMethod httpServiceMethod = this.httpServiceMethods.get(method);
			return httpServiceMethod.invoke(invocation.getArguments());
		}

	}

}
