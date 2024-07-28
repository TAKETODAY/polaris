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

package cn.taketoday.polaris.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

import cn.taketoday.polaris.beans.BeanMetadata;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.Nullable;
import cn.taketoday.polaris.util.PolarisProperties;

/**
 * resolve EnumerationValue
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/2 20:42
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class EnumerationValueTypeHandler<T extends Enum<T>> implements TypeHandler<T> {
  public static final String fallbackValueBeanPropertyKey = "polaris.jdbc.type-handler.enum-value-property-name";

  private static final String fallbackValueBeanProperty = PolarisProperties.getProperty(
          fallbackValueBeanPropertyKey, "value");

  private final T[] enumConstants;

  private final TypeHandler delegate;

  private final Function<T, Object> valueSupplier;

  public EnumerationValueTypeHandler(Class<T> type, TypeHandlerManager registry) {
    Assert.notNull(type, "Type argument is required");
    BeanProperty annotatedProperty = getAnnotatedProperty(type);
    this.enumConstants = type.getEnumConstants();
    if (annotatedProperty == null) {
      // fallback to Enum#name()
      this.valueSupplier = Enum::name;
      this.delegate = registry.getTypeHandler(String.class);
    }
    else {
      this.valueSupplier = annotatedProperty::getValue;
      this.delegate = registry.getTypeHandler(annotatedProperty);
    }
  }

  @Nullable
  static <T> BeanProperty getAnnotatedProperty(Class<T> type) {
    BeanProperty annotatedProperty = null;
    BeanMetadata metadata = BeanMetadata.forClass(type);
    for (BeanProperty beanProperty : metadata) {
      if (beanProperty.isAnnotationPresent(EnumerationValue.class)) {
        Assert.state(annotatedProperty == null, "@EnumerationValue must annotated on one property");
        annotatedProperty = beanProperty;
      }
    }

    if (annotatedProperty == null) {
      // fallback
      annotatedProperty = metadata.getBeanProperty(fallbackValueBeanProperty);
    }
    return annotatedProperty;
  }

  @Override
  public void setParameter(PreparedStatement ps, int parameterIndex, @Nullable T parameter) throws SQLException {
    if (parameter != null) {
      Object propertyValue = valueSupplier.apply(parameter);
      delegate.setParameter(ps, parameterIndex, propertyValue);
    }
    else {
      delegate.setParameter(ps, parameterIndex, null);
    }
  }

  @Nullable
  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    // get value in DB
    Object propertyValueInDb = delegate.getResult(rs, columnName);
    if (propertyValueInDb == null) {
      return null;
    }

    // convert, find target enum
    return convertToEnum(propertyValueInDb);
  }

  @Nullable
  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    // get value in DB
    Object propertyValueInDb = delegate.getResult(rs, columnIndex);
    if (propertyValueInDb == null) {
      return null;
    }

    // convert, find target enum
    return convertToEnum(propertyValueInDb);
  }

  @Nullable
  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    // get value in DB
    Object propertyValueInDb = delegate.getResult(cs, columnIndex);
    if (propertyValueInDb == null) {
      return null;
    }

    // convert, find target enum
    return convertToEnum(propertyValueInDb);
  }

  //

  @Nullable
  private T convertToEnum(Object propertyValueInDb) {
    for (T enumConstant : enumConstants) {
      Object propertyValue = valueSupplier.apply(enumConstant);
      if (equals(propertyValueInDb, propertyValue)) {
        // found
        return enumConstant;
      }
    }

    return null;
  }

  private static boolean equals(Object propertyValueInDb, Object propertyValue) {
    return Objects.equals(propertyValue, propertyValueInDb);
  }

}
