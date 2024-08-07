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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import cn.taketoday.polaris.annotation.Order;
import cn.taketoday.polaris.annotation.UpdateBy;
import cn.taketoday.polaris.jdbc.CannotGetJdbcConnectionException;
import cn.taketoday.polaris.jdbc.DefaultResultSetHandlerFactory;
import cn.taketoday.polaris.jdbc.GeneratedKeysException;
import cn.taketoday.polaris.jdbc.JdbcBeanMetadata;
import cn.taketoday.polaris.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import cn.taketoday.polaris.jdbc.PersistenceException;
import cn.taketoday.polaris.jdbc.RepositoryManager;
import cn.taketoday.polaris.jdbc.ResultSetExtractor;
import cn.taketoday.polaris.jdbc.support.JdbcAccessor;
import cn.taketoday.polaris.logging.LogMessage;
import cn.taketoday.polaris.platform.Platform;
import cn.taketoday.polaris.query.MappedStatement;
import cn.taketoday.polaris.sql.Insert;
import cn.taketoday.polaris.sql.OrderByClause;
import cn.taketoday.polaris.sql.Restriction;
import cn.taketoday.polaris.sql.SimpleSelect;
import cn.taketoday.polaris.sql.Update;
import cn.taketoday.polaris.transaction.TransactionConfig;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.CollectionUtils;
import cn.taketoday.polaris.util.Descriptive;
import cn.taketoday.polaris.util.Nullable;
import cn.taketoday.polaris.util.Pair;

/**
 * Default EntityManager implementation
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/9/10 22:28
 */
public class DefaultEntityManager extends JdbcAccessor implements EntityManager {

  private EntityMetadataFactory entityMetadataFactory = new DefaultEntityMetadataFactory();

  private final RepositoryManager repositoryManager;

  private int maxBatchRecords = 0;

  @Nullable
  private ArrayList<BatchPersistListener> batchPersistListeners;

  /**
   * a flag indicating whether auto-generated keys should be returned;
   */
  private boolean autoGenerateId = true;

  private PropertyUpdateStrategy defaultUpdateStrategy = PropertyUpdateStrategy.noneNull();

  private Pageable defaultPageable = Pageable.of(10, 1);

  private Platform platform = Platform.forClasspath();

  @Nullable
  private TransactionConfig transactionConfig = TransactionConfig.forDefaults();

  private QueryHandlerFactories handlerFactories = new QueryHandlerFactories(entityMetadataFactory);

  public DefaultEntityManager(RepositoryManager repositoryManager) {
    super(repositoryManager.getConnectionSource());
    this.repositoryManager = repositoryManager;
    setExceptionTranslator(repositoryManager.getExceptionTranslator());
  }

  public void setPlatform(@Nullable Platform platform) {
    this.platform = platform == null ? Platform.forClasspath() : platform;
  }

  public void setDefaultUpdateStrategy(PropertyUpdateStrategy defaultUpdateStrategy) {
    Assert.notNull(defaultUpdateStrategy, "defaultUpdateStrategy is required");
    this.defaultUpdateStrategy = defaultUpdateStrategy;
  }

  public void setDefaultPageable(Pageable defaultPageable) {
    Assert.notNull(defaultPageable, "defaultPageable is required");
    this.defaultPageable = defaultPageable;
  }

  public void setEntityMetadataFactory(EntityMetadataFactory entityMetadataFactory) {
    Assert.notNull(entityMetadataFactory, "entityMetadataFactory is required");
    this.entityMetadataFactory = entityMetadataFactory;
    this.handlerFactories = new QueryHandlerFactories(entityMetadataFactory);
  }

  /**
   * Set a flag indicating whether auto-generated keys should be returned;
   *
   * @param autoGenerateId a flag indicating whether auto-generated keys should be returned;
   */
  public void setAutoGenerateId(boolean autoGenerateId) {
    this.autoGenerateId = autoGenerateId;
  }

  /**
   * Sets the number of batched commands this Query allows to be added before
   * implicitly calling <code>executeBatch()</code> from
   * <code>addToBatch()</code>. <br/>
   *
   * When set to 0, executeBatch is not called implicitly. This is the default
   * behaviour. <br/>
   *
   * When using this, please take care about calling <code>executeBatch()</code>
   * after finished adding all commands to the batch because commands may remain
   * unexecuted after the last <code>addToBatch()</code> call. Additionally, if
   * fetchGeneratedKeys is set, then previously generated keys will be lost after
   * a batch is executed.
   *
   * @throws IllegalArgumentException Thrown if the value is negative.
   */
  public void setMaxBatchRecords(int maxBatchRecords) {
    Assert.isTrue(maxBatchRecords >= 0, "maxBatchRecords should be a non-negative value");
    this.maxBatchRecords = maxBatchRecords;
  }

  public int getMaxBatchRecords() {
    return this.maxBatchRecords;
  }

  public void addBatchPersistListeners(BatchPersistListener... listeners) {
    if (batchPersistListeners == null) {
      batchPersistListeners = new ArrayList<>();
    }
    CollectionUtils.addAll(batchPersistListeners, listeners);
  }

  public void addBatchPersistListeners(Collection<BatchPersistListener> listeners) {
    if (batchPersistListeners == null) {
      batchPersistListeners = new ArrayList<>();
    }
    batchPersistListeners.addAll(listeners);
  }

  public void setBatchPersistListeners(@Nullable Collection<BatchPersistListener> listeners) {
    if (listeners == null) {
      this.batchPersistListeners = null;
    }
    else {
      if (batchPersistListeners == null) {
        batchPersistListeners = new ArrayList<>();
      }
      else {
        batchPersistListeners.clear();
      }
      batchPersistListeners.addAll(listeners);
    }
  }

  /**
   * Set transaction config
   *
   * @param config the TransactionConfig instance (can be {@code null} for defaults),
   * describing propagation behavior, isolation level, timeout etc.
   */
  public void setTransactionConfig(@Nullable TransactionConfig config) {
    this.transactionConfig = config;
  }

  // ---------------------------------------------------------------------
  // Implementation of EntityManager
  // ---------------------------------------------------------------------

  @Override
  public int persist(Object entity) throws DataAccessException {
    return persist(entity, defaultUpdateStrategy(entity), autoGenerateId);
  }

  @Override
  public int persist(Object entity, boolean autoGenerateId) throws DataAccessException {
    return persist(entity, defaultUpdateStrategy(entity), autoGenerateId);
  }

  @Override
  public int persist(Object entity, @Nullable PropertyUpdateStrategy strategy) throws DataAccessException {
    return persist(entity, strategy, autoGenerateId);
  }

  @Override
  public int persist(Object entity, @Nullable PropertyUpdateStrategy strategy, boolean autoGenerateId) throws DataAccessException {
    EntityMetadata entityMetadata = entityMetadataFactory.getEntityMetadata(entity.getClass());
    if (strategy == null) {
      strategy = defaultUpdateStrategy(entity);
    }

    var pair = insertStatement(strategy, entity, entityMetadata);

    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Persisting entity: {}", entity), pair.first);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    ResultSet generatedKeys = null;
    try {
      autoGenerateId = autoGenerateId || entityMetadata.autoGeneratedId;
      statement = prepareStatement(con, pair.first, autoGenerateId);
      setParameters(entity, pair.second, statement);
      // execute
      int updateCount = statement.executeUpdate();
      if (autoGenerateId) {
        if (entityMetadata.idProperty != null) {
          try {
            generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
              entityMetadata.idProperty.setProperty(entity, generatedKeys, 1);
            }
          }
          catch (SQLException e) {
            throw new GeneratedKeysException("Cannot get generated keys", e);
          }
        }
      }
      return updateCount;
    }
    catch (SQLException ex) {
      throw translateException("Persisting entity", pair.first, ex);
    }
    finally {
      closeResource(con, statement, generatedKeys);
    }
  }

  @Override
  public void persist(Iterable<?> entities) throws DataAccessException {
    persist(entities, null, autoGenerateId);
  }

  @Override
  public void persist(Iterable<?> entities, boolean autoGenerateId) throws DataAccessException {
    persist(entities, null, autoGenerateId);
  }

  @Override
  public void persist(Iterable<?> entities, @Nullable PropertyUpdateStrategy strategy) throws DataAccessException {
    persist(entities, strategy, autoGenerateId);
  }

  @Override
  public void persist(Iterable<?> entities, @Nullable PropertyUpdateStrategy strategy, boolean autoGenerateId)
          throws DataAccessException //
  {
    try (var transaction = repositoryManager.beginTransaction(connectionSource, transactionConfig)) {
      int maxBatchRecords = getMaxBatchRecords();
      var statements = new HashMap<Class<?>, PreparedBatch>(8);
      try {
        for (Object entity : entities) {
          Class<?> entityType = entity.getClass();
          PreparedBatch batch = statements.get(entityType);
          if (batch == null) {
            EntityMetadata entityMetadata = entityMetadataFactory.getEntityMetadata(entityType);
            PropertyUpdateStrategy strategyToUse = strategy;
            if (strategyToUse == null) {
              strategyToUse = defaultUpdateStrategy(entity);
            }
            var pair = insertStatement(strategyToUse, entity, entityMetadata);
            batch = new PreparedBatch(transaction.getJdbcConnection(), pair.first, strategyToUse, entityMetadata,
                    pair.second, autoGenerateId || entityMetadata.autoGeneratedId);
            statements.put(entityType, batch);
          }
          batch.addBatchUpdate(entity, maxBatchRecords);
        }

        for (PreparedBatch preparedBatch : statements.values()) {
          preparedBatch.explicitExecuteBatch();
        }
        transaction.commit(false);
      }
      catch (Throwable ex) {
        transaction.rollback(false);
        if (ex instanceof DataAccessException dae) {
          throw dae;
        }
        if (ex instanceof SQLException se) {
          throw translateException("Batch persist entities Running in transaction", null, se);
        }
        throw new PersistenceException("Batch persist entities failed", ex);
      }
    }
  }

  @Override
  public int update(Object entity) throws DataAccessException {
    return update(entity, null);
  }

  @Override
  public int update(Object entity, @Nullable PropertyUpdateStrategy strategy) throws DataAccessException {
    if (strategy == null) {
      strategy = defaultUpdateStrategy(entity);
    }
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entity.getClass());
    EntityProperty idProperty = metadata.idProperty;
    if (idProperty != null) {
      Object id = idProperty.getValue(entity);
      if (id != null) {
        return doUpdateById(entity, id, idProperty, metadata, updateExcludeId(strategy));
      }
    }

    Update updateStmt = new Update(metadata.tableName);

    ArrayList<EntityProperty> properties = new ArrayList<>(4);
    ArrayList<EntityProperty> updateByProperties = new ArrayList<>(2);
    for (EntityProperty property : metadata.entityPropertiesExcludeId) {
      if (property.isPresent(UpdateBy.class)) {
        updateByProperties.add(property);
        updateStmt.addRestriction(property.columnName);
      }
      else if (strategy.shouldUpdate(entity, property)) {
        properties.add(property);
        updateStmt.addAssignment(property.columnName);
      }
    }

    if (properties.isEmpty()) {
      throw new InvalidDataAccessApiUsageException("Updating an entity, There is no update properties");
    }

    if (updateByProperties.isEmpty()) {
      throw new InvalidDataAccessApiUsageException("Updating an entity, There is no update by properties");
    }

    String sql = updateStmt.toStatementString(platform);

    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Updating entity using: '{}'", updateByProperties), sql);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    try {
      statement = con.prepareStatement(sql);
      int idx = setParameters(entity, properties, statement);
      // apply where parameters
      for (EntityProperty updateBy : updateByProperties) {
        updateBy.setTo(statement, idx++, entity);
      }
      return statement.executeUpdate();
    }
    catch (SQLException ex) {
      throw translateException("Updating entity", sql, ex);
    }
    finally {
      closeResource(con, statement);
    }
  }

  @Override
  public int updateById(Object entity) {
    return updateById(entity, null);
  }

  @Override
  public int updateById(Object entity, Object id) {
    return updateById(entity, id, null);
  }

  @Override
  public int updateById(Object entity, @Nullable PropertyUpdateStrategy strategy) {
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entity.getClass());
    EntityProperty idProperty = metadata.idProperty();

    Object id = idProperty.getValue(entity);
    if (id == null) {
      throw new InvalidDataAccessApiUsageException("Updating an entity, ID value is required");
    }

    if (strategy == null) {
      strategy = defaultUpdateStrategy(entity);
    }

    return doUpdateById(entity, id, idProperty, metadata, updateExcludeId(strategy));
  }

  /**
   * returns a new chain which exclude ID
   *
   * @return returns a new Strategy
   */
  private static PropertyUpdateStrategy updateExcludeId(PropertyUpdateStrategy strategy) {
    return (entity, property) -> !property.isIdProperty && strategy.shouldUpdate(entity, property);
  }

  @Override
  public int updateById(Object entity, Object id, @Nullable PropertyUpdateStrategy strategy) {
    Assert.notNull(id, "Entity id is required");
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entity.getClass());
    EntityProperty idProperty = metadata.idProperty();
    Assert.isTrue(idProperty.property.isInstance(id), "Entity Id matches failed");

    if (strategy == null) {
      strategy = defaultUpdateStrategy(entity);
    }

    return doUpdateById(entity, id, idProperty, metadata, strategy);
  }

  private int doUpdateById(Object entity, Object id, EntityProperty idProperty, EntityMetadata metadata, PropertyUpdateStrategy strategy) {
    Update updateStmt = new Update(metadata.tableName);
    updateStmt.addRestriction(idProperty.columnName);

    ArrayList<EntityProperty> properties = new ArrayList<>();
    for (EntityProperty property : metadata.entityProperties) {
      if (strategy.shouldUpdate(entity, property)) {
        updateStmt.addAssignment(property.columnName);
        properties.add(property);
      }
    }

    if (properties.isEmpty()) {
      throw new InvalidDataAccessApiUsageException("Updating an entity, There is no update properties");
    }

    String sql = updateStmt.toStatementString(platform);

    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Updating entity using ID: '{}'", id), sql);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    try {
      statement = con.prepareStatement(sql);
      int idx = setParameters(entity, properties, statement);
      // last one is ID
      idProperty.setParameter(statement, idx, id);
      return statement.executeUpdate();
    }
    catch (SQLException ex) {
      throw translateException("Updating entity By ID", sql, ex);
    }
    finally {
      closeResource(con, statement);
    }
  }

  @Override
  public int updateBy(Object entity, String where) {
    return updateBy(entity, where, null);
  }

  @Override
  public int updateBy(Object entity, String where, @Nullable PropertyUpdateStrategy strategy) {
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entity.getClass());

    Update updateStmt = new Update(metadata.tableName);

    if (strategy == null) {
      strategy = defaultUpdateStrategy(entity);
    }

    EntityProperty updateBy = null;
    ArrayList<EntityProperty> properties = new ArrayList<>();
    for (EntityProperty property : metadata.entityProperties) {
      // columnName or property name
      if (Objects.equals(where, property.columnName)
              || Objects.equals(where, property.property.getName())) {
        updateBy = property;
      }
      else if (strategy.shouldUpdate(entity, property)) {
        updateStmt.addAssignment(property.columnName);
        properties.add(property);
      }
    }

    if (updateBy == null) {
      throw new InvalidDataAccessApiUsageException("Updating an entity, 'where' property '%s' not found".formatted(where));
    }

    updateStmt.addRestriction(updateBy.columnName);

    Object updateByValue = updateBy.getValue(entity);
    if (updateByValue == null) {
      throw new InvalidDataAccessApiUsageException(
              "Updating an entity, 'where' property value '%s' is required".formatted(where));
    }

    String sql = updateStmt.toStatementString(platform);
    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Updating entity using {} : '{}'", where, updateByValue), sql);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    try {
      statement = con.prepareStatement(sql);
      int idx = setParameters(entity, properties, statement);
      // last one is where
      updateBy.setParameter(statement, idx, updateByValue);
      return statement.executeUpdate();
    }
    catch (SQLException ex) {
      throw translateException("Updating entity By " + where, sql, ex);
    }
    finally {
      closeResource(con, statement);
    }
  }

  @Override
  public int delete(Class<?> entityType, Object id) {
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entityType);

    if (metadata.idProperty == null) {
      throw new InvalidDataAccessApiUsageException("Deleting an entity, Id property not found");
    }

    StringBuilder sql = new StringBuilder();
    sql.append("DELETE FROM ");
    sql.append(metadata.tableName);
    sql.append(" WHERE `");
    sql.append(metadata.idProperty.columnName);
    sql.append("` = ? ");

    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Deleting entity using ID: {}", id), sql);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    try {
      statement = con.prepareStatement(sql.toString());
      metadata.idProperty.setParameter(statement, 1, id);
      return statement.executeUpdate();
    }
    catch (SQLException ex) {
      throw translateException("Deleting entity using ID", sql.toString(), ex);
    }
    finally {
      closeResource(con, statement);
    }
  }

  @Override
  public int delete(Object entityOrExample) {
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entityOrExample.getClass());

    Object id = null;
    if (metadata.idProperty != null) {
      id = metadata.idProperty.getValue(entityOrExample);
    }

    ExampleQuery exampleQuery = null;

    StringBuilder sql = new StringBuilder();
    sql.append("DELETE FROM ");
    sql.append(metadata.tableName);
    if (id != null) {
      // delete by id
      sql.append(" WHERE `");
      sql.append(metadata.idProperty.columnName);
      sql.append("` = ? ");
    }
    else {
      exampleQuery = new ExampleQuery(entityOrExample, metadata, DefaultQueryHandlerFactory.strategies);
      exampleQuery.renderWhereClause(sql);
    }

    if (stmtLogger.isDebugEnabled()) {
      stmtLogger.logStatement(LogMessage.format("Deleting entity: [{}]", entityOrExample), sql);
    }

    Connection con = getConnection();
    PreparedStatement statement = null;
    try {
      statement = con.prepareStatement(sql.toString());
      if (id != null) {
        metadata.idProperty.setParameter(statement, 1, id);
      }
      else {
        exampleQuery.setParameter(metadata, statement);
      }

      return statement.executeUpdate();
    }
    catch (SQLException ex) {
      throw translateException("Deleting entity", sql.toString(), ex);
    }
    finally {
      closeResource(con, statement);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Query methods
  // -----------------------------------------------------------------------------------------------

  /**
   * Find by primary key.
   * Search for an entity of the specified class and primary key.
   * If the entity instance is contained in the underlying repository,
   * it is returned from there.
   *
   * @param entityType entity class
   * @param id primary key
   * @return the found entity instance or null if the entity does
   * not exist
   * @throws IllegalArgumentException if the first argument does
   * not denote an entity type or the second argument is
   * is not a valid type for that entity's primary key or
   * is null
   */
  @Override
  @Nullable
  public <T> T findById(Class<T> entityType, Object id) throws DataAccessException {
    return iterate(entityType, new FindByIdQuery(id)).first();
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public <T> T findFirst(T entity) throws DataAccessException {
    return findFirst((Class<T>) entity.getClass(), entity);
  }

  @Nullable
  @Override
  public <T> T findFirst(Class<T> entityType, Object param) throws DataAccessException {
    return iterate(entityType, param).first();
  }

  @Override
  public <T> T findFirst(Class<T> entityType, @Nullable QueryStatement handler) throws DataAccessException {
    return iterate(entityType, handler).first();
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public <T> T findUnique(T example) throws DataAccessException {
    return iterate((Class<T>) example.getClass(), example).unique();
  }

  @Nullable
  @Override
  public <T> T findUnique(Class<T> entityType, Object param) throws DataAccessException {
    return iterate(entityType, param).unique();
  }

  @Override
  public <T> T findUnique(Class<T> entityType, @Nullable QueryStatement handler) throws DataAccessException {
    return iterate(entityType, handler).unique();
  }

  @Override
  public <T> List<T> find(Class<T> entityType) throws DataAccessException {
    return find(entityType, (QueryStatement) null);
  }

  @Override
  public <T> List<T> find(Class<T> entityType, Map<String, Order> sortKeys) throws DataAccessException {
    Assert.notEmpty(sortKeys, "sortKeys is required");
    return find(entityType, new NoConditionsOrderByQuery(OrderByClause.forMap(sortKeys)));
  }

  @Override
  public <T> List<T> find(Class<T> entityType, Pair<String, Order> sortKey) throws DataAccessException {
    Assert.notNull(sortKey, "sortKey is required");
    return find(entityType, new NoConditionsOrderByQuery(OrderByClause.mutable().orderBy(sortKey)));
  }

  @SafeVarargs
  @Override
  public final <T> List<T> find(Class<T> entityType, Pair<String, Order>... sortKeys) throws DataAccessException {
    return find(entityType, new NoConditionsOrderByQuery(OrderByClause.valueOf(sortKeys)));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> find(T example) throws DataAccessException {
    return iterate((Class<T>) example.getClass(), example).list();
  }

  @Override
  public <T> List<T> find(Class<T> entityType, Object param) throws DataAccessException {
    return iterate(entityType, param).list();
  }

  @Override
  public <T> List<T> find(Class<T> entityType, @Nullable QueryStatement handler) throws DataAccessException {
    return iterate(entityType, handler).list();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K, T> Map<K, T> find(T example, String mapKey) throws DataAccessException {
    return find((Class<T>) example.getClass(), example, mapKey);
  }

  @Override
  public <K, T> Map<K, T> find(Class<T> entityType, Object param, String mapKey) throws DataAccessException {
    return iterate(entityType, param).toMap(mapKey);
  }

  @Override
  public <K, T> Map<K, T> find(Class<T> entityType, @Nullable QueryStatement handler, String mapKey) throws DataAccessException {
    return iterate(entityType, handler).toMap(mapKey);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K, T> Map<K, T> find(T example, Function<T, K> keyMapper) throws DataAccessException {
    return find((Class<T>) example.getClass(), example, keyMapper);
  }

  @Override
  public <K, T> Map<K, T> find(Class<T> entityType, Object param, Function<T, K> keyMapper) throws DataAccessException {
    return iterate(entityType, param).toMap(keyMapper);
  }

  @Override
  public <K, T> Map<K, T> find(Class<T> entityType, @Nullable QueryStatement handler, Function<T, K> keyMapper) throws DataAccessException {
    return iterate(entityType, handler).toMap(keyMapper);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Number count(T example) throws DataAccessException {
    return count((Class<T>) example.getClass(), example);
  }

  @Override
  public <T> Number count(Class<T> entityType) throws DataAccessException {
    return count(entityType, null);
  }

  @Override
  public <T> Number count(Class<T> entityType, Object param) throws DataAccessException {
    return count(entityType, handlerFactories.createCondition(param));
  }

  @Override
  public <T> Page<T> page(T example) throws DataAccessException {
    return page(example, Pageable.unwrap(example));
  }

  @Override
  public <T> Page<T> page(Class<T> entityType, @Nullable Pageable pageable) throws DataAccessException {
    return page(entityType, null, pageable);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Page<T> page(T example, @Nullable Pageable pageable) throws DataAccessException {
    return page((Class<T>) example.getClass(), example, pageable);
  }

  @Override
  public <T> Page<T> page(Class<T> entityType, Object param) throws DataAccessException {
    return page(entityType, param, Pageable.unwrap(param));
  }

  @Override
  public <T> Page<T> page(Class<T> entityType, Object param, @Nullable Pageable pageable) throws DataAccessException {
    return page(entityType, handlerFactories.createCondition(param), pageable);
  }

  @Override
  public <T> Page<T> page(Class<T> entityType, @Nullable ConditionStatement handler) throws DataAccessException {
    return page(entityType, handler, Pageable.unwrap(handler));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> void iterate(T example, Consumer<T> entityConsumer) throws DataAccessException {
    iterate((Class<T>) example.getClass(), example, entityConsumer);
  }

  @Override
  public <T> void iterate(Class<T> entityType, Object param, Consumer<T> entityConsumer) throws DataAccessException {
    iterate(entityType, param).consume(entityConsumer);
  }

  @Override
  public <T> void iterate(Class<T> entityType, @Nullable QueryStatement handler, Consumer<T> entityConsumer) throws DataAccessException {
    iterate(entityType, handler).consume(entityConsumer);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> EntityIterator<T> iterate(T example) throws DataAccessException {
    return iterate((Class<T>) example.getClass(), example);
  }

  @Override
  public <T> EntityIterator<T> iterate(Class<T> entityType, Object param) throws DataAccessException {
    return iterate(entityType, handlerFactories.createQuery(param));
  }

  @Override
  public <T> EntityIterator<T> iterate(Class<T> entityType, String statementId) throws DataAccessException {
    return iterate(entityType, MappedStatement.forStatement(statementId));
  }

  @Override
  public <T> EntityIterator<T> iterate(Class<T> entityType, String statementId, @Nullable Object parameter) throws DataAccessException {
    return iterate(entityType, MappedStatement.forStatement(statementId, parameter));
  }

  @Override
  public <T> EntityIterator<T> iterate(Class<T> entityType, @Nullable QueryStatement handler) throws DataAccessException {
    if (handler == null) {
      handler = NoConditionsQuery.instance;
    }

    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entityType);
    String statement = handler.render(metadata).toStatementString(platform);

    Connection con = getConnection();
    try {
      PreparedStatement stmt = con.prepareStatement(statement);
      handler.setParameter(metadata, stmt);

      if (stmtLogger.isDebugEnabled()) {
        stmtLogger.logStatement(getDebugLogMessage(handler), statement);
      }

      return new DefaultEntityIterator<>(con, stmt, entityType, metadata);
    }
    catch (SQLException ex) {
      closeResource(con);
      throw translateException(getDescription(handler), statement, ex);
    }
  }

  @Override
  public <T> Number count(Class<T> entityType, @Nullable ConditionStatement handler) throws DataAccessException {
    if (handler == null) {
      handler = NoConditionsQuery.instance;
    }
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entityType);

    ArrayList<Restriction> restrictions = new ArrayList<>();
    handler.renderWhereClause(metadata, restrictions);
    Connection con = getConnection();
    try {
      return doQueryCount(metadata, handler, restrictions, con);
    }
    finally {
      closeResource(con);
    }
  }

  @Override
  public <T> Page<T> page(Class<T> entityType, @Nullable ConditionStatement handler, @Nullable Pageable pageable) throws DataAccessException {
    if (handler == null) {
      handler = NoConditionsQuery.instance;
    }

    if (pageable == null) {
      pageable = defaultPageable();
    }

    ArrayList<Restriction> restrictions = new ArrayList<>();
    EntityMetadata metadata = entityMetadataFactory.getEntityMetadata(entityType);
    handler.renderWhereClause(metadata, restrictions);

    Connection con = getConnection();
    String statement = null;
    PreparedStatement stmt = null;
    try {
      Number count = doQueryCount(metadata, handler, restrictions, con);
      if (count.intValue() < 1) {
        // no record
        closeResource(con);
        return new Page<>(pageable, 0, Collections.emptyList());
      }

      statement = new SimpleSelect(Arrays.asList(metadata.columnNames), restrictions)
              .setTableName(metadata.tableName)
              .pageable(pageable)
              .orderBy(handler.getOrderByClause(metadata))
              .toStatementString(platform);

      stmt = con.prepareStatement(statement);
      handler.setParameter(metadata, stmt);

      if (stmtLogger.isDebugEnabled()) {
        stmtLogger.logStatement(getDebugLogMessage(handler), statement);
      }

      return new Page<>(pageable, count,
              new DefaultEntityIterator<T>(con, stmt, entityType, metadata).list(pageable.pageSize()));
    }
    catch (Throwable ex) {
      closeResource(con, stmt);
      if (ex instanceof DataAccessException dae) {
        throw dae;
      }
      if (ex instanceof SQLException) {
        throw translateException(getDescription(handler), statement, (SQLException) ex);
      }
      throw new DataRetrievalFailureException("Unable to retrieve the pageable data ", ex);
    }
  }

  private Number doQueryCount(EntityMetadata metadata, ConditionStatement handler, ArrayList<Restriction> restrictions, Connection con) throws DataAccessException {
    StringBuilder countSql = new StringBuilder(restrictions.size() * 10 + 25 + metadata.tableName.length());
    countSql.append("SELECT COUNT(*) FROM `")
            .append(metadata.tableName)
            .append('`');

    Restriction.render(restrictions, countSql);

    String statement = countSql.toString();
    ResultSet resultSet = null;
    PreparedStatement stmt = null;
    try {
      stmt = con.prepareStatement(statement);
      handler.setParameter(metadata, stmt);

      if (stmtLogger.isDebugEnabled()) {
        stmtLogger.logStatement(getDebugLogMessage(handler), statement);
      }
      resultSet = stmt.executeQuery();
      if (resultSet.next()) {
        return resultSet.getLong(1);
      }
      return 0;
    }
    catch (SQLException ex) {
      closeResource(con);
      throw translateException(getDescription(handler), statement, ex);
    }
    finally {
      closeResource(null, stmt, resultSet);
    }
  }

  /**
   * 获取 JDBC 连接
   *
   * @return JDBC 连接
   * @throws CannotGetJdbcConnectionException JDBC 连接获取失败
   */
  protected Connection getConnection() throws CannotGetJdbcConnectionException {
    try {
      return connectionSource.getConnection();
    }
    catch (SQLException | IllegalStateException ex) {
      throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
    }
  }

  /**
   * default Pageable
   */
  protected Pageable defaultPageable() {
    return defaultPageable;
  }

  /**
   * get default PropertyUpdateStrategy
   */
  protected PropertyUpdateStrategy defaultUpdateStrategy(Object entity) {
    if (entity instanceof UpdateStrategySource source) {
      return source.updateStrategy();
    }
    if (entity instanceof PropertyUpdateStrategy strategy) {
      return strategy;
    }
    return defaultUpdateStrategy;
  }

  protected PreparedStatement prepareStatement(Connection connection, String sql, boolean autoGenerateId) throws SQLException {
    if (autoGenerateId) {
      return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }
    return connection.prepareStatement(sql);
  }

  private Pair<String, ArrayList<EntityProperty>> insertStatement(PropertyUpdateStrategy strategy, Object entity, EntityMetadata entityMetadata) {
    Insert insert = new Insert(entityMetadata.tableName);
    var properties = new ArrayList<EntityProperty>(entityMetadata.entityProperties.length);
    for (EntityProperty property : entityMetadata.entityProperties) {
      if (strategy.shouldUpdate(entity, property)) {
        insert.addColumn(property.columnName);
        properties.add(property);
      }
    }

    return Pair.of(insert.toStatementString(platform), properties);
  }

  private void closeResource(@Nullable Connection connection) {
    if (connection != null) {
      try {
        connectionSource.releaseConnection(connection);
      }
      catch (SQLException e) {
        if (repositoryManager.isCatchResourceCloseErrors()) {
          throw translateException("Closing Connection", null, e);
        }
        else {
          logger.debug("Could not close JDBC Connection", e);
        }
      }
    }
  }

  private void closeResource(@Nullable Connection connection, @Nullable PreparedStatement statement) {
    closeResource(connection);
    if (statement != null) {
      try {
        statement.close();
      }
      catch (SQLException e) {
        if (repositoryManager.isCatchResourceCloseErrors()) {
          throw translateException("Closing Statement", null, e);
        }
        else {
          logger.debug("Could not close JDBC Statement", e);
        }
      }
    }
  }

  private void closeResource(@Nullable Connection connection, @Nullable PreparedStatement statement, @Nullable ResultSet resultSet) {
    closeResource(connection, statement);
    if (resultSet != null) {
      try {
        resultSet.close();
      }
      catch (SQLException e) {
        if (repositoryManager.isCatchResourceCloseErrors()) {
          throw translateException("Closing ResultSet", null, e);
        }
        else {
          logger.debug("Could not close JDBC ResultSet", e);
        }
      }
    }
  }

  private String getDescription(Object handler) {
    Descriptive descriptive = null;
    if (handler instanceof Descriptive) {
      descriptive = (Descriptive) handler;
    }
    if (descriptive == null) {
      descriptive = NoConditionsQuery.instance;
    }
    return descriptive.getDescription();
  }

  private Object getDebugLogMessage(Object handler) {
    if (handler instanceof DebugDescriptive descriptive) {
      return descriptive.getDebugLogMessage();
    }
    else if (handler instanceof Descriptive) {
      return LogMessage.format(((Descriptive) handler).getDescription());
    }
    return NoConditionsQuery.instance.getDebugLogMessage();
  }

  //

  private static int setParameters(Object entity, ArrayList<EntityProperty> properties, PreparedStatement statement) throws SQLException {
    int idx = 1;
    for (EntityProperty property : properties) {
      property.setTo(statement, idx++, entity);
    }
    return idx;
  }

  private static void assertUpdateCount(String sql, int actualCount, int expectCount) {
    if (actualCount != expectCount) {
      throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(sql, expectCount, actualCount);
    }
  }

  private final class DefaultEntityIterator<T> extends EntityIterator<T> {

    private final Connection connection;

    private final PreparedStatement statement;

    private final ResultSetExtractor<T> handler;

    private DefaultEntityIterator(Connection connection, PreparedStatement statement, Class<?> entityType, EntityMetadata entityMetadata) throws SQLException {
      super(statement.executeQuery(), entityMetadata);
      this.statement = statement;
      this.connection = connection;
      try {
        var factory = new DefaultResultSetHandlerFactory<T>(new JdbcBeanMetadata(entityType, repositoryManager.isDefaultCaseSensitive(),
                true, true), repositoryManager, null);
        this.handler = factory.getResultSetHandler(resultSet.getMetaData());
      }
      catch (SQLException e) {
        throw translateException("Get ResultSetHandler", null, e);
      }
    }

    @Override
    protected T readNext(ResultSet resultSet) throws SQLException {
      return handler.extractData(resultSet);
    }

    @Override
    protected RuntimeException handleReadError(SQLException ex) {
      return translateException("Reading Entity", null, ex);
    }

    @Override
    public void close() {
      closeResource(connection, statement, resultSet);
    }

  }

  final class PreparedBatch extends BatchExecution {

    public final PreparedStatement statement;

    public final ArrayList<EntityProperty> properties;

    public int currentBatchRecords = 0;

    PreparedBatch(Connection connection, String sql, PropertyUpdateStrategy strategy,
            EntityMetadata entityMetadata, ArrayList<EntityProperty> properties, boolean autoGenerateId) throws SQLException {
      super(sql, strategy, entityMetadata, autoGenerateId);
      this.properties = properties;
      this.statement = prepareStatement(connection, sql, autoGenerateId);
    }

    public void addBatchUpdate(Object entity, int maxBatchRecords) throws Throwable {
      entities.add(entity);
      PreparedStatement statement = this.statement;
      setParameters(entity, properties, statement);
      statement.addBatch();
      if (maxBatchRecords > 0 && ++currentBatchRecords % maxBatchRecords == 0) {
        executeBatch(statement, true);
      }
    }

    public void explicitExecuteBatch() throws Throwable {
      executeBatch(statement, false);
      closeResource(null, statement);
    }

    private void executeBatch(PreparedStatement statement, boolean implicitExecution) throws Throwable {
      beforeProcessing(implicitExecution);
      if (stmtLogger.isDebugEnabled()) {
        stmtLogger.logStatement(LogMessage.format("Executing batch size: {}", entities.size()), sql);
      }
      Throwable exception = null;
      try {
        int[] updateCounts = statement.executeBatch();
        assertUpdateCount(sql, updateCounts.length, entities.size());

        if (autoGenerateId) {
          EntityProperty idProperty = entityMetadata.idProperty;
          if (idProperty != null) {
            ResultSet generatedKeys = statement.getGeneratedKeys();
            for (Object entity : entities) {
              try {
                if (generatedKeys.next()) {
                  idProperty.setProperty(entity, generatedKeys, 1);
                }
              }
              catch (SQLException e) {
                throw new GeneratedKeysException("Cannot get generated keys", e);
              }
            }
          }
        }
      }
      catch (Throwable e) {
        exception = e;
        throw e;
      }
      finally {
        afterProcessing(implicitExecution, exception);
        this.currentBatchRecords = 0;
        this.entities.clear();
      }
    }

    private void afterProcessing(boolean implicitExecution, @Nullable Throwable exception) {
      if (CollectionUtils.isNotEmpty(batchPersistListeners)) {
        for (BatchPersistListener listener : batchPersistListeners) {
          listener.afterProcessing(this, implicitExecution, exception);
        }
      }
    }

    private void beforeProcessing(boolean implicitExecution) {
      if (CollectionUtils.isNotEmpty(batchPersistListeners)) {
        for (BatchPersistListener listener : batchPersistListeners) {
          listener.beforeProcessing(this, implicitExecution);
        }
      }
    }

  }

}
