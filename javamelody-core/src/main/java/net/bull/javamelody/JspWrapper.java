/*
 * Copyright 2008-2019 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bull.javamelody;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import net.bull.javamelody.internal.common.Parameters;
import net.bull.javamelody.internal.model.Counter;

/**
 * Wrapping de l'interface javax.servlet.RequestDispatcher pour avoir les temps moyens de rendu
 * des pages JSP.
 * @author Emeric Vernat
 */
final class JspWrapper implements InvocationHandler {
	private static final Counter JSP_COUNTER = new Counter(Counter.JSP_COUNTER_NAME, "jsp.png",
			JdbcWrapper.SINGLETON.getSqlCounter());
	private static final boolean COUNTER_HIDDEN = Parameters.isCounterHidden(JSP_COUNTER.getName());
	private static final boolean DISABLED = Parameter.DISABLED.getValueAsBoolean();

	private final String path;
	private final RequestDispatcher requestDispatcher;

	private static class HttpRequestWrapper extends HttpServletRequestWrapper {
		private final HttpServletResponse response;

		/**
		 * Constructs a request object wrapping the given request.
		 * @param request HttpServletRequest
		 * @param response HttpServletResponse
		 */
		HttpRequestWrapper(HttpServletRequest request, HttpServletResponse response) {
			super(request);
			this.response = response;
		}

		/** {@inheritDoc} */
		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			final RequestDispatcher requestDispatcher = super.getRequestDispatcher(path);
			if (requestDispatcher == null) {
				return null;
			}
			// il n'est pas dit que path soit non null
			final InvocationHandler invocationHandler = new JspWrapper(String.valueOf(path),
					requestDispatcher);
			return JdbcWrapper.createProxy(requestDispatcher, invocationHandler);
		}

		@Override
		public AsyncContext startAsync() {
			// issue 217: after MonitoringFilter.doFilter, response is instance of CounterServletResponseWrapper,
			// and if response.getWriter() has been called before calling request.startAsync(),
			// then asyncContext.getResponse() should return the instance of CounterServletResponseWrapper
			// and not the initial response without the wrapper,
			// otherwise asyncContext.getResponse().getWriter() will throw something like
			// "IllegalStateException: getOutputStream() has already been called for this response"
			return super.startAsync(this, response);
		}
	}

	/**
	 * Constructeur.
	 * @param path String
	 * @param requestDispatcher RequestDispatcher
	 */
	JspWrapper(String path, RequestDispatcher requestDispatcher) {
		super();
		assert path != null;
		assert requestDispatcher != null;
		// quand ce RequestDispatcher est utilisé, le compteur est affiché
		// sauf si le paramètre displayed-counters dit le contraire
		JSP_COUNTER.setDisplayed(!COUNTER_HIDDEN);
		JSP_COUNTER.setUsed(true);
		this.path = path;
		this.requestDispatcher = requestDispatcher;
	}

	static HttpServletRequest createHttpRequestWrapper(HttpServletRequest request,
			HttpServletResponse response) {
		if (DISABLED || COUNTER_HIDDEN) {
			return request;
		}
		return new HttpRequestWrapper(request, response);
	}

	static Counter getJspCounter() {
		return JSP_COUNTER;
	}

	/**
	 * Intercepte une exécution de méthode sur une façade.
	 * @param proxy Object
	 * @param method Method
	 * @param args Object[]
	 * @return Object
	 * @throws Throwable t
	 */
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		final String methodName = method.getName();
		// != for perf (strings interned: != is ok)
		if ("include" != methodName && "forward" != methodName) { // NOPMD
			return method.invoke(requestDispatcher, args);
		}
		boolean systemError = false;
		try {
			final String pathWithoutParameters;
			final int indexOf = path.indexOf('?');
			if (indexOf != -1) {
				pathWithoutParameters = path.substring(0, indexOf);
			} else {
				pathWithoutParameters = path;
			}
			JSP_COUNTER.bindContextIncludingCpu(pathWithoutParameters);
			return method.invoke(requestDispatcher, args);
		} catch (final InvocationTargetException e) {
			if (e.getCause() instanceof Error) {
				// on catche Error pour avoir les erreurs systèmes
				// mais pas Exception qui sont fonctionnelles en général
				systemError = true;
			}
			throw e;
		} finally {
			// on enregistre la requête dans les statistiques
			JSP_COUNTER.addRequestForCurrentContext(systemError);
		}
	}
}
