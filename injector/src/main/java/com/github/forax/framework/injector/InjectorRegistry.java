package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class InjectorRegistry {
  private final HashMap<Class<?>, Supplier<?>> map = new HashMap<>();

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    map.put(type, () -> instance);
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> supplier) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(supplier);
    map.put(type, supplier);
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var supplier = map.getOrDefault(type, () -> {
      throw new IllegalStateException("Instance not found");
    });
    return type.cast(supplier.get());
  }

  public static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    Objects.requireNonNull(type);
    var propertyDescriptors = Utils.beanInfo(type).getPropertyDescriptors();
    return Stream.of(propertyDescriptors)
        .filter(property -> {
          var setter = property.getWriteMethod();
          return setter != null && setter.isAnnotationPresent(Inject.class);
        })
        .toList();
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> implementation) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(implementation);
    var constructor = getInjectedConstructor(implementation)
        .orElseGet(() -> Utils.defaultConstructor(implementation));
    var parameters = constructor.getParameterTypes();
    var properties = findInjectableProperties(implementation);

    registerProvider(type, () -> {
      var args = Arrays.stream(parameters)
          .map(this::lookupInstance)
          .toArray();
      var instance = type.cast(Utils.newInstance(constructor, args));
      injectDefaultValues(instance, properties);
      return instance;
    });
  }

  public <T> void registerProviderClass(Class<T> type) {
    registerProviderClass(type, type);
  }

  private void injectDefaultValues(Object instance, List<PropertyDescriptor> properties) {
    properties.forEach(property -> {
      var setter = property.getWriteMethod();
      var value = lookupInstance(property.getPropertyType());
      Utils.invokeMethod(instance, setter, value);
    });
  }

  private static Optional<Constructor<?>> getInjectedConstructor(Class<?> type) {
    var constructors = Arrays.stream(type.getConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
        .toList();
    return switch (constructors.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(constructors.get(0));
      default -> throw new IllegalStateException("Only one constructor should be injected");
    };
  }
 }
