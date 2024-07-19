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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 2021/2/12 13:51
 */
public class BytesInputStreamTypeHandler extends BaseTypeHandler<InputStream> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int parameterIndex, InputStream parameter) throws SQLException {
    ps.setBinaryStream(parameterIndex, parameter);
  }

  @Override
  public InputStream getResult(ResultSet rs, String columnName) throws SQLException {
    byte[] bytes = rs.getBytes(columnName);
    return bytes != null ? new ByteArrayInputStream(bytes) : null;
  }

  @Override
  public InputStream getResult(ResultSet rs, int columnIndex) throws SQLException {
    byte[] bytes = rs.getBytes(columnIndex);
    return bytes != null ? new ByteArrayInputStream(bytes) : null;
  }

  @Override
  public InputStream getResult(CallableStatement cs, int columnIndex) throws SQLException {
    byte[] bytes = cs.getBytes(columnIndex);
    return bytes != null ? new ByteArrayInputStream(bytes) : null;
  }

}
