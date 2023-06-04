package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class InterceptorRegistry {
  private final HashMap<Class<?>, List<Interceptor>> interceptors = new HashMap<>();
  private final HashMap<Method, Invocation> invocationCache = new HashMap<>();

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(aroundAdvice);
    addInterceptor(annotationClass, getCorrespondingInterceptor(aroundAdvice));
  }

  private Interceptor getCorrespondingInterceptor(AroundAdvice advice) {
    return (instance, method, args, invocation) -> {
      advice.before(instance, method, args);
      var result = invocation.proceed(instance, method, args);
      advice.after(instance, method, args, result);
      return result;
    };
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(delegate);

    InvocationHandler invocationHandler =
        (proxy, method, args) -> getInvocation(method).proceed(delegate, method, args);

    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, invocationHandler));
  }

  private Invocation getInvocation(Method method) {
    return invocationCache.computeIfAbsent(method, m -> getInvocation(findInterceptors(m)));
  }

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptors.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
    invocationCache.clear();
  }

  List<Interceptor> findInterceptors(Method method) {
    Objects.requireNonNull(method);
    return Stream.of(
            Arrays.stream(method.getDeclaringClass().getAnnotations()),
            Arrays.stream(method.getAnnotations()),
            Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
        .flatMap(s -> s)
        .map(Annotation::annotationType)
        .distinct()
        .map(interceptors::get)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .toList();
  }

  static Invocation getInvocation(List<? extends Interceptor> interceptorList) {
    Objects.requireNonNull(interceptorList);
    return Utils.reverseList(interceptorList)
        .stream()
        .reduce(
            Utils::invokeMethod,
            (invocation, interceptor) -> (instance, method, args) -> interceptor.intercept(instance, method, args, invocation),
            (a, b) -> { throw new AssertionError(); }
        );
  }
}
