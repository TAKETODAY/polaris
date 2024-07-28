/*
 * Copyright 2024 the original author or authors.
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

package cn.taketoday.polaris.beans;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.MapCache;
import cn.taketoday.polaris.util.Nullable;
import cn.taketoday.polaris.util.ReflectionUtils;

/**
 * @author <a href="https://github.com/TAKETODAY">海子 Yang</a>
 * @since 1.0 2024/7/23 14:14
 */
public class BeanMetadata implements Iterable<BeanProperty> {

  private static final MapCache<Class<?>, BeanMetadata, ?> metadataMappings = new MapCache<>(
          new ConcurrentHashMap<>(), BeanMetadata::new);

  private final Class<?> beanClass;

  private BeanInstantiator instantiator;

  /**
   * @since 1.0
   */
  private BeanPropertiesHolder propertyHolder;

  public BeanMetadata(Class<?> beanClass) {
    this.beanClass = beanClass;
  }

  public Class<?> getType() {
    return this.beanClass;
  }

  public BeanInstantiator getInstantiator() {
    BeanInstantiator instantiator = this.instantiator;
    if (instantiator == null) {
      instantiator = BeanInstantiator.forClass(beanClass);
      this.instantiator = instantiator;
    }
    return instantiator;
  }

  /**
   * Create this bean a new instance with no arguments
   *
   * @return a new instance object
   */
  public Object newInstance() {
    return newInstance(null);
  }

  /**
   * Create this bean a new instance with given arguments
   *
   * @return a new instance object
   */
  public Object newInstance(@Nullable Object[] args) {
    return getInstantiator().instantiate(args);
  }

  @Nullable
  public BeanProperty getBeanProperty(String propertyName) {
    return getBeanProperties().get(propertyName);
  }

  /**
   * Get {@link BeanProperty} with given name
   *
   * @param propertyName property name
   * @return target {@link BeanProperty}
   */
  public BeanProperty obtainBeanProperty(String propertyName) {
    BeanProperty beanProperty = getBeanProperty(propertyName);
    Assert.state(beanProperty != null, () -> "property not found: " + propertyName);
    return beanProperty;
  }

  /**
   * Set a value to root object
   *
   * @param root Root object
   * @param propertyName Property name
   * @param value new value to set
   * @see #obtainBeanProperty(String)
   */
  public void setProperty(Object root, String propertyName, Object value) {
    obtainBeanProperty(propertyName).setValue(root, value);
  }

  /**
   * Get property value
   *
   * @param root Root object
   * @param propertyName Property name
   * @see #obtainBeanProperty(String)
   */
  public Object getProperty(Object root, String propertyName) {
    return obtainBeanProperty(propertyName).getValue(root);
  }

  /**
   * Get property type
   *
   * @param propertyName Property name
   * @see #obtainBeanProperty(String)
   */
  public Class<?> getPropertyType(String propertyName) {
    return obtainBeanProperty(propertyName).getType();
  }

  /**
   * Get properties mapping
   *
   * @return map of properties
   */
  public HashMap<String, BeanProperty> getBeanProperties() {
    return propertyHolder().mapping;
  }

  /**
   * Get list of properties
   *
   * <p>
   * Note: not read-only
   *
   * @return list of properties
   */
  public ArrayList<BeanProperty> beanProperties() {
    return propertyHolder().beanProperties;
  }

  /**
   * @since 1.0
   */
  public int getPropertySize() {
    return propertyHolder().beanProperties.size();
  }

  /**
   * @since 1.0
   */
  public boolean containsProperty(String name) {
    return propertyHolder().mapping.containsKey(name);
  }

  /**
   * @since 1.0
   */
  private BeanPropertiesHolder propertyHolder() {
    BeanPropertiesHolder propertyHolder = this.propertyHolder;
    if (propertyHolder == null) {
      propertyHolder = BeanPropertiesMapCache.computeProperties(this);
      this.propertyHolder = propertyHolder;
    }
    return propertyHolder;
  }

  public HashMap<String, BeanProperty> createBeanProperties() {
    HashMap<String, BeanProperty> beanPropertyMap = new LinkedHashMap<>();

    try {
      PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(beanClass).getPropertyDescriptors();
      for (PropertyDescriptor descriptor : propertyDescriptors) {
        if (descriptor.getReadMethod() != null || descriptor.getWriteMethod() != null) {
          BeanProperty property = new BeanProperty(descriptor, beanClass);
          beanPropertyMap.put(descriptor.getName(), property);
        }
      }

      ReflectionUtils.doWithFields(beanClass, field -> {
        if (!Modifier.isStatic(field.getModifiers())) {
          String propertyName = getPropertyName(field);
          if (!beanPropertyMap.containsKey(propertyName)) {
            BeanProperty property = new FieldBeanProperty(field);
            beanPropertyMap.put(propertyName, property);
          }
        }
      });

      return beanPropertyMap;
    }
    catch (IntrospectionException e) {
      throw new IllegalStateException("introspection error", e);
    }
  }

  private String getPropertyName(Field field) {
    // todo maybe start with 'm,_'
    return field.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof BeanMetadata that))
      return false;
    return beanClass.equals(that.beanClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(beanClass);
  }

  //---------------------------------------------------------------------
  // Implementation of Iterable interface
  //---------------------------------------------------------------------

  @Override
  public Iterator<BeanProperty> iterator() {
    return propertyHolder().beanProperties.iterator();
  }

  @Override
  public void forEach(Consumer<? super BeanProperty> action) {
    propertyHolder().beanProperties.forEach(action);
  }

  @Override
  public Spliterator<BeanProperty> spliterator() {
    return propertyHolder().beanProperties.spliterator();
  }

  //---------------------------------------------------------------------
  // static factory method
  //---------------------------------------------------------------------

  /**
   * Create a {@link BeanMetadata} with given bean class
   *
   * @param beanClass target bean class cannot be simple class
   * @return {@link BeanMetadata}
   */
  public static BeanMetadata forClass(Class<?> beanClass) {
    return metadataMappings.get(beanClass);
  }

  /**
   * Create a {@link BeanMetadata} with given bean class
   *
   * @param object target bean cannot be simple object
   * @return {@link BeanMetadata}
   */
  public static BeanMetadata forInstance(Object object) {
    return forClass(object.getClass());
  }

  /**
   * @since 1.0
   */
  static final class BeanPropertiesHolder {
    public final HashMap<String, BeanProperty> mapping;
    public final ArrayList<BeanProperty> beanProperties;

    BeanPropertiesHolder(HashMap<String, BeanProperty> mapping) {
      this.mapping = new HashMap<>(mapping);
      this.beanProperties = new ArrayList<>(mapping.values());
    }
  }

  /**
   * Mapping cache
   */
  static class BeanPropertiesMapCache extends MapCache<BeanMetadata, BeanPropertiesHolder, BeanMetadata> {
    private static final BeanPropertiesMapCache beanPropertiesMappings = new BeanPropertiesMapCache();

    BeanPropertiesMapCache() {
      super(new ConcurrentHashMap<>());
    }

    static BeanPropertiesHolder computeProperties(BeanMetadata metadata) {
      return beanPropertiesMappings.get(metadata);
    }

    @Override
    protected BeanPropertiesHolder createValue(BeanMetadata key, BeanMetadata param) {
      HashMap<String, BeanProperty> propertyMap = key.createBeanProperties();
      return new BeanPropertiesHolder(propertyMap);
    }

  }

}
