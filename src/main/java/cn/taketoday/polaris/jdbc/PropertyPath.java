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

package cn.taketoday.polaris.jdbc;

import cn.taketoday.polaris.beans.BeanMetadata;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.beans.BeanUtils;
import cn.taketoday.polaris.util.Nullable;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/7/30 20:31
 */
final class PropertyPath {
  static final String emptyPlaceholder = "<not-found>";

  @Nullable
  public final PropertyPath next;

  // @Nullable check first
  public final BeanProperty beanProperty;

  public PropertyPath(Class<?> objectType, String propertyPath) {
    BeanMetadata metadata = BeanMetadata.forClass(objectType);
    int pos = BeanUtils.getFirstNestedPropertySeparatorIndex(propertyPath);
    String name = propertyPath.substring(0, pos);
    this.beanProperty = metadata.obtainBeanProperty(name);

    BeanMetadata nextMetadata = BeanMetadata.forClass(beanProperty.getType());
    this.next = new PropertyPath(propertyPath.substring(pos + 1), nextMetadata);
  }

  public PropertyPath(String propertyPath, BeanMetadata metadata) {
    int pos = BeanUtils.getFirstNestedPropertySeparatorIndex(propertyPath);
    if (pos > -1) {
      // compute next PropertyPath
      String propertyName = propertyPath.substring(0, pos);
      this.beanProperty = metadata.getBeanProperty(propertyName);
      if (beanProperty != null) {
        BeanMetadata nextMetadata = BeanMetadata.forClass(beanProperty.getType());
        this.next = new PropertyPath(propertyPath.substring(pos + 1), nextMetadata);
      }
      else {
        this.next = null;
      }
    }
    else {
      // terminated (last PropertyPath)
      this.next = null;
      this.beanProperty = metadata.getBeanProperty(propertyPath); // maybe null
    }
  }

  @Nullable
  public BeanProperty getNestedBeanProperty() {
    if (next != null) {
      return next.getNestedBeanProperty();
    }
    return beanProperty;
  }

  public Object getNestedObject(Object parent) {
    if (next != null) {
      Object nextParent = getProperty(parent);
      return next.getNestedObject(nextParent);
    }
    return parent;
  }

  public void set(Object obj, @Nullable Object result) {
    PropertyPath current = this;
    while (current.next != null) {
      obj = getProperty(obj);
      current = current.next;
    }

    // set current object's property
    current.beanProperty.setValue(obj, result);
  }

  private Object getProperty(Object obj) {
    Object property = beanProperty.getValue(obj);
    if (property == null) {
      // nested object maybe null
      property = beanProperty.instantiate();
      beanProperty.setValue(obj, property);
    }
    return property;
  }

  @Override
  public String toString() {
    if (next != null) {
      StringBuilder sb = new StringBuilder();
      if (beanProperty == null) {
        sb.append(emptyPlaceholder);
      }
      else {
        sb.append(beanProperty.getName());
      }
      return sb.append('.').append(next).toString();
    }
    if (beanProperty == null) {
      return emptyPlaceholder;
    }
    return beanProperty.getName();
  }
}
