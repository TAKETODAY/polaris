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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import cn.taketoday.polaris.type.ConversionException;
import cn.taketoday.polaris.type.ConversionService;
import cn.taketoday.polaris.type.TypeHandler;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.CollectionUtils;
import cn.taketoday.polaris.util.Nullable;

/**
 * Batch execution result
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2023/1/17 11:25
 */
public class BatchResult extends ExecutionResult {

  @Nullable
  private int[] batchResult;

  @Nullable
  private ArrayList<Object> generatedKeys;

  @Nullable
  private Integer affectedRows;

  public BatchResult(JdbcConnection connection) {
    super(connection);
  }

  /**
   * Get last batch execution result
   * <p>
   *
   * Maybe explicitly execution
   *
   * @see NamedQuery#addToBatch()
   * @see PreparedStatement#executeBatch()
   */
  public int[] getLastBatchResult() {
    if (batchResult == null) {
      throw new PersistenceException(
              "It is required to call executeBatch() method before calling getLastBatchResult().");
    }
    return batchResult;
  }

  void setBatchResult(int[] batchResult) {
    this.batchResult = batchResult;
    if (this.affectedRows == null) {
      this.affectedRows = batchResult.length;
    }
    else {
      this.affectedRows += batchResult.length;
    }
  }

  /**
   * @return the number of rows updated or deleted
   */
  public int getAffectedRows() {
    if (affectedRows == null) {
      throw new PersistenceException(
              "It is required to call executeBatch() method before calling getAffectedRows().");
    }
    return affectedRows;
  }

  // ------------------------------------------------
  // -------------------- Keys ----------------------
  // ------------------------------------------------

  <T> void addKeys(ResultSet rs, TypeHandler<T> handler) {
    ArrayList<Object> keys = this.generatedKeys;
    if (keys == null) {
      keys = new ArrayList<>();
      this.generatedKeys = keys;
    }
    try (rs) {
      while (rs.next()) {
        T generatedKey = handler.getResult(rs, 1);
        keys.add(generatedKey);
      }
    }
    catch (SQLException e) {
      throw translateException("Getting generated keys.", e);
    }
  }

  void addKeys(ResultSet rs) {
    ArrayList<Object> keys = this.generatedKeys;
    if (keys == null) {
      keys = new ArrayList<>();
      this.generatedKeys = keys;
    }
    try {
      while (rs.next()) {
        keys.add(rs.getObject(1));
      }
    }
    catch (SQLException e) {
      throw translateException("Getting generated keys.", e);
    }
  }

  @Nullable
  public Object getKey() {
    assertCanGetKeys();
    List<Object> keys = this.generatedKeys;
    if (CollectionUtils.isNotEmpty(keys)) {
      return keys.get(0);
    }
    return null;
  }

  /**
   * @throws GeneratedKeysConversionException Generated Keys conversion failed
   * @throws IllegalArgumentException If conversionService is null
   */
  public <V> V getKey(Class<V> returnType) {
    return getKey(returnType, getManager().getConversionService());
  }

  /**
   * @throws GeneratedKeysConversionException Generated Keys conversion failed
   * @throws IllegalArgumentException If conversionService is null
   */
  public <V> V getKey(Class<V> returnType, ConversionService conversionService) {
    Assert.notNull(conversionService, "conversionService is required");
    Object key = getKey();
    try {
      return conversionService.convert(key, returnType);
    }
    catch (ConversionException e) {
      throw new GeneratedKeysConversionException(
              "Exception occurred while converting value from database to type " + returnType.toString(), e);
    }
  }

  public Object[] getKeys() {
    assertCanGetKeys();
    if (generatedKeys != null) {
      return generatedKeys.toArray();
    }
    return null;
  }

  /**
   * @throws GeneratedKeysConversionException cannot converting value from database
   * @throws IllegalArgumentException If conversionService is null
   */
  @Nullable
  public <V> List<V> getKeys(Class<V> returnType) {
    return getKeys(returnType, getManager().getConversionService());
  }

  /**
   * @throws GeneratedKeysConversionException cannot converting value from database
   * @throws IllegalArgumentException If conversionService is null
   */
  @Nullable
  public <V> List<V> getKeys(Class<V> returnType, ConversionService conversionService) {
    assertCanGetKeys();
    if (generatedKeys != null) {
      Assert.notNull(conversionService, "conversionService is required");
      try {
        ArrayList<V> convertedKeys = new ArrayList<>(generatedKeys.size());
        for (Object key : generatedKeys) {
          convertedKeys.add(conversionService.convert(key, returnType));
        }
        return convertedKeys;
      }
      catch (ConversionException e) {
        throw new GeneratedKeysConversionException(
                "Exception occurred while converting value from database to type " + returnType, e);
      }
    }
    return null;
  }

  private void assertCanGetKeys() {
    if (generatedKeys == null) {
      throw new GeneratedKeysException(
              "Keys where not fetched from database." +
                      " Please set the returnGeneratedKeys parameter " +
                      "in the createQuery() method to enable fetching of generated keys.");
    }

  }

}
