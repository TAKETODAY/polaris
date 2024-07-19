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

package cn.taketoday.polaris.jdbc.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import cn.taketoday.lang.Nullable;

/**
 * Duration Type handler
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2023/7/11 14:23
 */
public class DurationTypeHandler extends BaseTypeHandler<Duration> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int parameterIndex, Duration parameter) throws SQLException {
    ps.setLong(parameterIndex, parameter.toNanos());
  }

  @Nullable
  @Override
  public Duration getResult(ResultSet rs, String columnName) throws SQLException {
    long nanos = rs.getLong(columnName);
    if (nanos == 0) {
      if (rs.wasNull()) {
        return null;
      }
      return Duration.ZERO;
    }
    return Duration.ofNanos(nanos);
  }

  @Nullable
  @Override
  public Duration getResult(ResultSet rs, int columnIndex) throws SQLException {
    long nanos = rs.getLong(columnIndex);
    if (nanos == 0) {
      if (rs.wasNull()) {
        return null;
      }
      return Duration.ZERO;
    }
    return Duration.ofNanos(nanos);
  }

  @Nullable
  @Override
  public Duration getResult(CallableStatement cs, int columnIndex) throws SQLException {
    long nanos = cs.getLong(columnIndex);
    if (nanos == 0) {
      if (cs.wasNull()) {
        return null;
      }
      return Duration.ZERO;
    }
    return Duration.ofNanos(nanos);
  }

}
