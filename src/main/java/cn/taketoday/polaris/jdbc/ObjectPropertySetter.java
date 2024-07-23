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

import java.sql.ResultSet;
import java.sql.SQLException;

import cn.taketoday.core.conversion.ConversionException;
import cn.taketoday.core.conversion.ConversionService;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.jdbc.type.TypeHandler;

/**
 * for any pojo
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see #setTo(Object, ResultSet, int)
 * @since 2021/1/7 22:49
 */
final class ObjectPropertySetter {

  @Nullable
  private final PropertyPath propertyPath;

  private final BeanProperty beanProperty; // cache

  private final TypeHandler<?> typeHandler;

  private final ConversionService conversionService;

  @Nullable
  private final PrimitiveTypeNullHandler primitiveTypeNullHandler;

  public ObjectPropertySetter(@Nullable PropertyPath propertyPath, BeanProperty beanProperty, RepositoryManager manager) {
    this(propertyPath, beanProperty, manager.getConversionService(),
            manager.getTypeHandler(beanProperty), manager.getPrimitiveTypeNullHandler());
  }

  public ObjectPropertySetter(@Nullable PropertyPath propertyPath,
          BeanProperty beanProperty, ConversionService conversionService,
          TypeHandler<?> typeHandler, @Nullable PrimitiveTypeNullHandler primitiveTypeNullHandler) {
    Assert.notNull(typeHandler, "TypeHandler is required");
    Assert.notNull(beanProperty, "BeanProperty is required");
    Assert.notNull(conversionService, "ConversionService is required");
    this.typeHandler = typeHandler;
    this.propertyPath = propertyPath;
    this.beanProperty = beanProperty;
    this.conversionService = conversionService;
    this.primitiveTypeNullHandler = primitiveTypeNullHandler;
  }

  /**
   * Set the data to {@code obj} from given {@code columnIndex} and {@link ResultSet}
   *
   * @param obj object to set
   * @param resultSet jdbc resultSet
   * @param columnIndex current column index
   * @throws SQLException when data fetch failed
   */
  public void setTo(Object obj, ResultSet resultSet, int columnIndex) throws SQLException {
    Object result = getResult(resultSet, columnIndex);
    if (result == null && beanProperty.isPrimitive()) {
      if (primitiveTypeNullHandler != null) {
        if (propertyPath != null) {
          obj = propertyPath.getNestedObject(obj);
        }
        primitiveTypeNullHandler.handleNull(beanProperty, obj);
      }
    }
    else {
      if (propertyPath != null) {
        propertyPath.set(obj, result);
      }
      else {
        beanProperty.setValue(obj, result);
      }
    }
  }

  /**
   * Get result from {@link ResultSet}.
   * <p>
   * Obtain from {@link TypeHandler}, if it fails, use the {@link ResultSet#getObject(int) default acquisition method }
   * </p>
   *
   * @param resultSet Target result set
   * @return data object
   * @throws SQLException If {@link ResultSet#getObject(int)} failed
   */
  @Nullable
  private Object getResult(ResultSet resultSet, int columnIndex) throws SQLException {
    try {
      return typeHandler.getResult(resultSet, columnIndex);
    }
    catch (SQLException e) {
      // maybe data conversion error
      Object object = resultSet.getObject(columnIndex);
      if (object == null || beanProperty.isInstance(object)) {
        return object;
      }
      try {
        return conversionService.convert(object, beanProperty.getTypeDescriptor());
      }
      catch (ConversionException ex) {
        if (ex.getCause() instanceof IllegalArgumentException cause) {
          String message = cause.getMessage();
          if ("A null value cannot be assigned to a primitive type".equals(message)) {
            // primitive type
            return null;
          }
        }
        e.addSuppressed(ex);
        throw e;
      }
    }
  }

}
