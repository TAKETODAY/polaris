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

package cn.taketoday.polaris;

import java.lang.annotation.Annotation;
import java.util.Set;

import cn.taketoday.polaris.annotation.Transient;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.util.Assert;

/**
 * PropertyFilter is to determine witch property not included in
 * an entity
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/16 22:29
 */
public interface PropertyFilter {

  /**
   * @param property bean property
   * @return is property not map to a column
   */
  boolean isFiltered(BeanProperty property);

  /**
   * returns a new resolving chain
   *
   * @param next next resolver
   * @return returns a new resolving chain
   */
  default PropertyFilter and(PropertyFilter next) {
    return beanProperty -> isFiltered(beanProperty) || next.isFiltered(beanProperty);
  }

  /**
   * filter property names
   *
   * @param filteredNames property names not mapping to database column
   */
  static PropertyFilter filteredNames(Set<String> filteredNames) {
    Assert.notEmpty(filteredNames, "filteredNames is empty");
    return property -> filteredNames.contains(property.getName());
  }

  /**
   * Accept any property
   */
  static PropertyFilter acceptAny() {
    return property -> false;
  }

  /**
   * use {@link Transient}
   */
  static PropertyFilter forTransientAnnotation() {
    return forAnnotation(Transient.class);
  }

  /**
   * Use input {@code annotationType} to filter
   *
   * @param annotationType Annotation type
   * @return Annotation based {@link PropertyFilter}
   */
  static PropertyFilter forAnnotation(Class<? extends Annotation> annotationType) {
    Assert.notNull(annotationType, "annotationType is required");
    return property -> property.isAnnotationPresent(annotationType);
  }

}
