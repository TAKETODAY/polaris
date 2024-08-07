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

import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.Nullable;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/6/20 22:22
 */
public final class AnyTypeHandler<T> implements TypeHandler<T> {

  private final Class<T> type;

  public AnyTypeHandler(Class<T> type) {
    Assert.notNull(type, "type is required");
    this.type = type;
  }

  @Override
  public void setParameter(PreparedStatement ps, int parameterIndex, @Nullable T parameter) throws SQLException {
    ps.setObject(parameterIndex, parameter);
  }

  @Nullable
  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    return rs.getObject(columnName, type);
  }

  @Nullable
  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    return rs.getObject(columnIndex, type);
  }

  @Nullable
  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    return cs.getObject(columnIndex, type);
  }

}
