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
import java.util.List;

import cn.taketoday.polaris.annotation.Column;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.util.AnnotationUtils;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.Nullable;
import cn.taketoday.polaris.util.StringUtils;

/**
 * Column name discover
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/16 22:03
 */
public interface ColumnNameDiscover {

  /**
   * determine that {@code property} 's column name
   *
   * @param property candidate
   * @return get column name
   */
  @Nullable
  String getColumnName(BeanProperty property);

  // static

  /**
   * returns a new resolving chain
   *
   * @param next next resolver
   * @return returns a new resolving chain
   */
  default ColumnNameDiscover and(ColumnNameDiscover next) {
    return property -> {
      String columnName = getColumnName(property);
      if (columnName == null) {
        return next.getColumnName(property);
      }
      return columnName;
    };
  }

  static ColumnNameDiscover composite(ColumnNameDiscover... discovers) {
    Assert.notNull(discovers, "ColumnNameDiscover is required");
    return composite(List.of(discovers));
  }

  static ColumnNameDiscover composite(List<ColumnNameDiscover> discovers) {
    Assert.notNull(discovers, "ColumnNameDiscover is required");
    return property -> {

      for (ColumnNameDiscover discover : discovers) {
        String columnName = discover.getColumnName(property);
        if (columnName != null) {
          return columnName;
        }
      }

      return null;
    };
  }

  /**
   * just use the property-name as the column-name
   */
  static ColumnNameDiscover forPropertyName() {
    return BeanProperty::getName;
  }

  /**
   * property-name to underscore (camelCase To Underscore)
   *
   * @see StringUtils#camelCaseToUnderscore(String)
   */
  static ColumnNameDiscover camelCaseToUnderscore() {
    return property -> {
      String propertyName = property.getName();
      return StringUtils.camelCaseToUnderscore(propertyName);
    };
  }

  /**
   * use {@link Column#value()}
   */
  static ColumnNameDiscover forColumnAnnotation() {
    return forAnnotation(Column.class);
  }

  /**
   * Use input {@code annotationType} to resolve column name
   *
   * @param annotationType Annotation type
   * @return Annotation based {@link ColumnNameDiscover}
   */
  static ColumnNameDiscover forAnnotation(Class<? extends Annotation> annotationType) {
    return forAnnotation(annotationType, "value");
  }

  /**
   * Use input {@code annotationType} and {@code attributeName} to resolve column name
   *
   * @param annotationType Annotation type
   * @param attributeName the attribute name
   * @return Annotation based {@link ColumnNameDiscover}
   */
  static ColumnNameDiscover forAnnotation(Class<? extends Annotation> annotationType, String attributeName) {
    Assert.notNull(attributeName, "attributeName is required");
    Assert.notNull(annotationType, "annotationType is required");

    return property -> {
      Annotation annotation = property.getAnnotation(annotationType);
      if (annotation != null) {
        String columnName = (String) AnnotationUtils.getValue(annotation, attributeName);
        if (StringUtils.hasText(columnName)) {
          return columnName;
        }
      }

      return null;
    };
  }

}
