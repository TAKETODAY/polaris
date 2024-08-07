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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import cn.taketoday.polaris.DataAccessException;
import cn.taketoday.polaris.DefaultEntityManager;
import cn.taketoday.polaris.EntityManager;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.jdbc.datasource.ConnectionSource;
import cn.taketoday.polaris.jdbc.parsing.QueryParameter;
import cn.taketoday.polaris.jdbc.parsing.SqlParameterParser;
import cn.taketoday.polaris.jdbc.support.JdbcAccessor;
import cn.taketoday.polaris.jdbc.support.JdbcTransactionManager;
import cn.taketoday.polaris.query.MapperFactory;
import cn.taketoday.polaris.query.MapperProvider;
import cn.taketoday.polaris.transaction.Isolation;
import cn.taketoday.polaris.transaction.TransactionConfig;
import cn.taketoday.polaris.transaction.TransactionManager;
import cn.taketoday.polaris.type.ConversionService;
import cn.taketoday.polaris.type.DefaultConversionService;
import cn.taketoday.polaris.type.TypeHandler;
import cn.taketoday.polaris.type.TypeHandlerManager;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.Nullable;

/**
 * RepositoryManager is the main class for the polaris library.
 * <p>
 * An <code>RepositoryManager</code> instance represents a way of connecting to one specific
 * database. To create a new instance, one need to specify either jdbc-url,
 * username and password for the database or a data source.
 * <p>
 * Internally the RepositoryManager instance uses a data source to create jdbc connections
 * to the database. If url, username and password was specified in the
 * constructor, a simple data source is created, which works as a simple wrapper
 * around the jdbc driver.
 * <p>
 * This library is learned from <a href="https://github.com/aaberg/sql2o">Sql2o</a>
 *
 * @author Hubery Huang
 * @author <a href="https://github.com/aaberg">Lars Aaberg</a>
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0
 */
public class RepositoryManager extends JdbcAccessor implements QueryProducer, MapperProvider {

  private final TransactionManager transactionManager;

  private final MapperFactory mapperFactory = new MapperFactory(this);

  private TypeHandlerManager typeHandlerManager = TypeHandlerManager.sharedInstance;

  private boolean defaultCaseSensitive;

  private boolean generatedKeys = true;

  private boolean catchResourceCloseErrors = false;

  @Nullable
  private Map<String, String> defaultColumnMappings;

  private SqlParameterParser sqlParameterParser = new SqlParameterParser();

  private ConversionService conversionService = new DefaultConversionService();

  @Nullable
  private PrimitiveTypeNullHandler primitiveTypeNullHandler;

  @Nullable
  private EntityManager entityManager;

  /**
   * 创建一个 RepositoryManager 实例
   *
   * @param url JDBC database url
   * @param user database username
   * @param pass database password
   */
  public RepositoryManager(String url, String user, String pass) {
    this(ConnectionSource.of(url, user, pass));
  }

  /**
   * 创建一个 RepositoryManager 实例，使用 JDBC 连接源
   *
   * @param dataSource The DataSource RepositoryManager uses to acquire connections to the database.
   */
  public RepositoryManager(DataSource dataSource) {
    this(ConnectionSource.forDataSource(dataSource));
  }

  /**
   * 创建一个 RepositoryManager 实例，使用 JDBC 连接源和事务管理器
   *
   * @param connectionSource JDBC 连接源，RepositoryManager 用来获取 JDBC 的连接
   */
  public RepositoryManager(ConnectionSource connectionSource) {
    this(connectionSource, new JdbcTransactionManager(connectionSource));
  }

  public RepositoryManager(ConnectionSource connectionSource, TransactionManager transactionManager) {
    super(connectionSource);
    Assert.notNull(transactionManager, "transactionManager is required");
    this.transactionManager = transactionManager;
  }

  /**
   * 设置全局的 XML 配置
   *
   * @param mapperLocation mapper 地址，从 classpath 下读取
   */
  public void setMapperLocation(@Nullable String mapperLocation) {
    mapperFactory.setMapperLocation(mapperLocation);
  }

  /**
   * Gets the default column mappings Map. column mappings added to this Map are
   * always available when RepositoryManager attempts to map between result sets and object
   * instances.
   *
   * @return The {@code Map<String,String>} instance, which RepositoryManager internally uses
   * to map column names with property names.
   */
  @Nullable
  public Map<String, String> getDefaultColumnMappings() {
    return defaultColumnMappings;
  }

  /**
   * Sets the default column mappings Map.
   *
   * @param defaultColumnMappings A {@link Map} instance RepositoryManager uses
   * internally to map between column names and property names.
   */
  public void setDefaultColumnMappings(@Nullable Map<String, String> defaultColumnMappings) {
    this.defaultColumnMappings = defaultColumnMappings;
  }

  /**
   * Gets value indicating if this instance of RepositoryManager is case sensitive when
   * mapping between columns names and property names.
   */
  public boolean isDefaultCaseSensitive() {
    return defaultCaseSensitive;
  }

  /**
   * Sets a value indicating if this instance of RepositoryManager is case-sensitive when
   * mapping between columns names and property names. This should almost always
   * be false, because most relational databases are not case-sensitive.
   */
  public void setDefaultCaseSensitive(boolean defaultCaseSensitive) {
    this.defaultCaseSensitive = defaultCaseSensitive;
  }

  public void setGeneratedKeys(boolean generatedKeys) {
    this.generatedKeys = generatedKeys;
  }

  /**
   * @return true if queries should return generated keys by default, false
   * otherwise
   */
  public boolean isGeneratedKeys() {
    return generatedKeys;
  }

  public void setSqlParameterParser(SqlParameterParser sqlParameterParser) {
    Assert.notNull(sqlParameterParser, "SqlParameterParser is required");
    this.sqlParameterParser = sqlParameterParser;
  }

  public SqlParameterParser getSqlParameterParser() {
    return sqlParameterParser;
  }

  public void setTypeHandlerManager(@Nullable TypeHandlerManager typeHandlerManager) {
    this.typeHandlerManager =
            typeHandlerManager == null ? TypeHandlerManager.sharedInstance : typeHandlerManager;
  }

  public TypeHandlerManager getTypeHandlerManager() {
    return typeHandlerManager;
  }

  /**
   * set {@link ConversionService} to convert keys or other object
   *
   * @param conversionService ConversionService
   */
  public void setConversionService(@Nullable ConversionService conversionService) {
    this.conversionService = conversionService == null
            ? new DefaultConversionService() : conversionService;
  }

  public ConversionService getConversionService() {
    return conversionService;
  }

  /**
   * set {@link PrimitiveTypeNullHandler}
   * to handle null values when property is PrimitiveType
   *
   * @param primitiveTypeNullHandler PrimitiveTypeNullHandler
   */
  public void setPrimitiveTypeNullHandler(@Nullable PrimitiveTypeNullHandler primitiveTypeNullHandler) {
    this.primitiveTypeNullHandler = primitiveTypeNullHandler;
  }

  /**
   * @return {@link PrimitiveTypeNullHandler}
   */
  @Nullable
  public PrimitiveTypeNullHandler getPrimitiveTypeNullHandler() {
    return primitiveTypeNullHandler;
  }

  public void setEntityManager(@Nullable EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public EntityManager getEntityManager() {
    if (entityManager == null) {
      entityManager = new DefaultEntityManager(this);
    }
    return entityManager;
  }

  public void setCatchResourceCloseErrors(boolean catchResourceCloseErrors) {
    this.catchResourceCloseErrors = catchResourceCloseErrors;
  }

  public boolean isCatchResourceCloseErrors() {
    return catchResourceCloseErrors;
  }

  /**
   * Return the transaction management strategy to be used.
   */
  public TransactionManager getTransactionManager() {
    return this.transactionManager;
  }

  //

  protected String parse(String sql, Map<String, QueryParameter> paramNameToIdxMap) {
    return sqlParameterParser.parse(sql, paramNameToIdxMap);
  }

  // ---------------------------------------------------------------------
  // Implementation of QueryProducer methods
  // ---------------------------------------------------------------------

  /**
   * Creates a {@link Query}
   * <p>
   * better to use :
   * create queries with {@link JdbcConnection} class instead,
   * using try-with-resource blocks
   * <pre> {@code
   * try (Connection con = repositoryManager.open()) {
   *    return repositoryManager.createQuery(query, name, returnGeneratedKeys)
   *                .fetch(Pojo.class);
   * }
   * }</pre>
   * </p>
   *
   * @param query the sql query string
   * @param returnGeneratedKeys boolean value indicating if the database should return any
   * generated keys.
   * @return the {@link NamedQuery} instance
   */
  @Override
  public Query createQuery(String query, boolean returnGeneratedKeys) {
    return open(true).createQuery(query, returnGeneratedKeys);
  }

  /**
   * Creates a {@link Query}
   *
   * better to use :
   * create queries with {@link JdbcConnection} class instead,
   * using try-with-resource blocks
   * <pre>{@code
   *     try (Connection con = repositoryManager.open()) {
   *         return repositoryManager.createQuery(query, name)
   *                      .fetch(Pojo.class);
   *     }
   *  }</pre>
   *
   * @param query the sql query string
   * @return the {@link NamedQuery} instance
   */
  @Override
  public Query createQuery(String query) {
    return open(true).createQuery(query);
  }

  /**
   * Creates a {@link NamedQuery}
   * <p>
   * better to use :
   * create queries with {@link JdbcConnection} class instead,
   * using try-with-resource blocks
   * <pre>{@code
   * try (Connection con = repositoryManager.open()) {
   *    return repositoryManager.createNamedQuery(query, name, returnGeneratedKeys)
   *                .fetch(Pojo.class);
   * }
   * }</pre>
   * </p>
   *
   * @param query the sql query string
   * @param returnGeneratedKeys boolean value indicating if the database should return any
   * generated keys.
   * @return the {@link NamedQuery} instance
   */
  @Override
  public NamedQuery createNamedQuery(String query, boolean returnGeneratedKeys) {
    return open(true).createNamedQuery(query, returnGeneratedKeys);
  }

  /**
   * Creates a {@link NamedQuery}
   *
   * better to use :
   * create queries with {@link JdbcConnection} class instead,
   * using try-with-resource blocks
   * <pre>{@code
   *     try (Connection con = repositoryManager.open()) {
   *         return repositoryManager.createNamedQuery(query, name)
   *                      .fetch(Pojo.class);
   *     }
   *  }</pre>
   *
   * @param query the sql query string
   * @return the {@link NamedQuery} instance
   */
  @Override
  public NamedQuery createNamedQuery(String query) {
    return open(true).createNamedQuery(query);
  }

  // JdbcConnection

  /**
   * Opens a connection to the database
   *
   * @return instance of the {@link JdbcConnection} class.
   */
  public JdbcConnection open() {
    return open(false);
  }

  /**
   * Opens a connection to the database
   *
   * @return instance of the {@link JdbcConnection} class.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection open(boolean autoClose) {
    return new JdbcConnection(this, getConnectionSource(), autoClose);
  }

  /**
   * Opens a connection to the database
   *
   * @param connection the {@link Connection}
   * @return instance of the {@link JdbcConnection} class.
   */
  public JdbcConnection open(Connection connection) {
    NestedConnection nested = new NestedConnection(connection);
    return new JdbcConnection(this, ConnectionSource.valueOf(nested), false);
  }

  /**
   * Opens a connection to the database
   *
   * @param dataSource the {@link DataSource} implementation substitution, that
   * will be used instead of one from {@link RepositoryManager} instance.
   * @return instance of the {@link JdbcConnection} class.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection open(DataSource dataSource) {
    return new JdbcConnection(this, ConnectionSource.forDataSource(dataSource), false);
  }

  /**
   * Invokes the run method on the {@link ResultStatementRunnable}
   * instance. This method guarantees that the connection is closed properly, when
   * either the run method completes or if an exception occurs.
   *
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V withConnection(ResultStatementRunnable<V, P> runnable, @Nullable P argument) {
    try (JdbcConnection connection = open()) {
      return runnable.run(connection, argument);
    }
    catch (DataAccessException e) {
      throw e;
    }
    catch (SQLException e) {
      throw translateException("Executing StatementRunnable", null, e);
    }
    catch (Throwable t) {
      throw new PersistenceException("An error occurred while executing StatementRunnable", t);
    }
  }

  /**
   * Invokes the run method on the {@link ResultStatementRunnable}
   * instance. This method guarantees that the connection is closed properly, when
   * either the run method completes or if an exception occurs.
   *
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V withConnection(ResultStatementRunnable<V, P> runnable) {
    return withConnection(runnable, null);
  }

  /**
   * Invokes the run method on the {@link ResultStatementRunnable}
   * instance. This method guarantees that the connection is closed properly, when
   * either the run method completes or if an exception occurs.
   *
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <T> void withConnection(StatementRunnable<T> runnable) {
    withConnection(runnable, null);
  }

  /**
   * Invokes the run method on the {@link ResultStatementRunnable}
   * instance. This method guarantees that the connection is closed properly, when
   * either the run method completes or if an exception occurs.
   *
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <T> void withConnection(StatementRunnable<T> runnable, @Nullable T argument) {
    try (JdbcConnection connection = open()) {
      runnable.run(connection, argument);
    }
    catch (DataAccessException e) {
      throw e;
    }
    catch (SQLException e) {
      throw translateException("Executing StatementRunnable", null, e);
    }
    catch (Throwable t) {
      throw new PersistenceException("An error occurred while executing StatementRunnable", t);
    }
  }

  /**
   * Begins a transaction with isolation level
   * {@link Connection#TRANSACTION_READ_COMMITTED}. Every statement
   * executed on the return {@link JdbcConnection} instance, will be executed in the
   * transaction. It is very important to always call either the
   * {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection beginTransaction() {
    return beginTransaction(Connection.TRANSACTION_READ_COMMITTED);
  }

  /**
   * Begins a transaction with the given isolation level. Every statement executed
   * on the return {@link JdbcConnection} instance, will be executed in the
   * transaction. It is very important to always call either the
   * {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @param isolationLevel the isolation level of the transaction
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection beginTransaction(int isolationLevel) {
    return beginTransaction(getConnectionSource(), TransactionConfig.forIsolationLevel(isolationLevel));
  }

  /**
   * Begins a transaction with the given isolation level. Every statement executed
   * on the return {@link JdbcConnection} instance, will be executed in the
   * transaction. It is very important to always call either the
   * {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @param isolationLevel the isolation level of the transaction
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection beginTransaction(Isolation isolationLevel) {
    return beginTransaction(getConnectionSource(), TransactionConfig.forIsolationLevel(isolationLevel));
  }

  /**
   * Begins a transaction with the given {@link TransactionConfig}.
   * Every statement executed on the return {@link JdbcConnection} instance,
   * will be executed in the transaction. It is very important to always
   * call either the {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @param config the TransactionConfig instance (can be {@code null} for defaults),
   * describing propagation behavior, isolation level, timeout etc.
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection beginTransaction(@Nullable TransactionConfig config) {
    return beginTransaction(getConnectionSource(), config);
  }

  /**
   * Begins a transaction with the {@link TransactionConfig}. Every statement executed
   * on the return {@link JdbcConnection} instance, will be executed in the
   * transaction. It is very important to always call either the
   * {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @param source the {@link DataSource} implementation substitution, that
   * will be used instead of one from {@link RepositoryManager} instance.
   * @param config the TransactionConfig instance (can be {@code null} for defaults),
   * describing propagation behavior, isolation level, timeout etc.
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public JdbcConnection beginTransaction(ConnectionSource source, @Nullable TransactionConfig config) {
    JdbcConnection connection = new JdbcConnection(this, source, false);
    connection.beginTransaction(config);
    return connection;
  }

  /**
   * Begins a transaction with isolation level
   * {@link Connection#TRANSACTION_READ_COMMITTED}. Every statement
   * executed on the return {@link JdbcConnection} instance, will be executed in the
   * transaction. It is very important to always call either the
   * {@link JdbcConnection#commit()} method or the
   * {@link JdbcConnection#rollback()} method to close the transaction. Use
   * proper try-catch logic.
   *
   * @param root the {@link Connection}
   * @return the {@link JdbcConnection} instance to use to run statements in the
   * transaction.
   */
  public JdbcConnection beginTransaction(Connection root) {
    JdbcConnection connection = open(root);
    boolean success = false;
    try {
      root.setAutoCommit(false);
      root.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      success = true;
    }
    catch (SQLException e) {
      throw translateException("Setting transaction options", null, e);
    }
    finally {
      if (!success) {
        connection.close();
      }
    }

    return connection;
  }

  /**
   * Calls the {@link StatementRunnable#run(JdbcConnection, Object)} method on the
   * {@link StatementRunnable} parameter. All statements run on the
   * {@link JdbcConnection} instance in the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method will be executed
   * in a transaction. The transaction will automatically be committed if the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method finishes without
   * throwing an exception. If an exception is thrown within the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method, the transaction
   * will automatically be rolled back.
   * <p>
   * The isolation level of the transaction will be set to
   * {@link Connection#TRANSACTION_READ_COMMITTED}
   *
   * @param runnable The {@link StatementRunnable} instance.
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <T> void runInTransaction(StatementRunnable<T> runnable) {
    runInTransaction(runnable, null);
  }

  /**
   * Calls the {@link StatementRunnable#run(JdbcConnection, Object)} method on the
   * {@link StatementRunnable} parameter. All statements run on the
   * {@link JdbcConnection} instance in the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method will be executed
   * in a transaction. The transaction will automatically be committed if the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method finishes without
   * throwing an exception. If an exception is thrown within the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method, the transaction
   * will automatically be rolled back.
   * <p>
   * The isolation level of the transaction will be set to
   * {@link Connection#TRANSACTION_READ_COMMITTED}
   *
   * @param runnable The {@link StatementRunnable} instance.
   * @param argument An argument which will be forwarded to the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <T> void runInTransaction(StatementRunnable<T> runnable, @Nullable T argument) {
    runInTransaction(runnable, argument, Connection.TRANSACTION_READ_COMMITTED);
  }

  /**
   * Calls the {@link StatementRunnable#run(JdbcConnection, Object)} method on the
   * {@link StatementRunnable} parameter. All statements run on the
   * {@link JdbcConnection} instance in the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method will be executed
   * in a transaction. The transaction will automatically be committed if the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method finishes without
   * throwing an exception. If an exception is thrown within the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method, the transaction
   * will automatically be rolled back.
   *
   * @param runnable The {@link StatementRunnable} instance.
   * @param argument An argument which will be forwarded to the
   * {@link StatementRunnable#run(JdbcConnection, Object) run} method
   * @param isolationLevel The isolation level of the transaction
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <T> void runInTransaction(StatementRunnable<T> runnable, @Nullable T argument, int isolationLevel) {
    JdbcConnection connection = beginTransaction(isolationLevel);
    connection.setRollbackOnException(false);

    try {
      runnable.run(connection, argument);
    }
    catch (Throwable throwable) {
      connection.rollback();
      if (throwable instanceof DataAccessException e) {
        throw e;
      }
      else if (throwable instanceof SQLException e) {
        throw translateException("Running in transaction", null, e);
      }
      throw new PersistenceException("An error occurred while executing StatementRunnable. Transaction is rolled back.", throwable);
    }
    connection.commit();
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V runInTransaction(ResultStatementRunnable<V, P> runnable) {
    return runInTransaction(runnable, null);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V runInTransaction(ResultStatementRunnable<V, P> runnable, @Nullable P argument) {
    return runInTransaction(runnable, argument, Connection.TRANSACTION_READ_COMMITTED);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V runInTransaction(ResultStatementRunnable<V, P> runnable, @Nullable P argument, int isolationLevel) {
    return runInTransaction(runnable, argument, TransactionConfig.forIsolationLevel(isolationLevel));
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V runInTransaction(ResultStatementRunnable<V, P> runnable, @Nullable P argument, Isolation isolation) {
    return runInTransaction(runnable, argument, TransactionConfig.forIsolationLevel(isolation));
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  public <V, P> V runInTransaction(ResultStatementRunnable<V, P> runnable,
          @Nullable P argument, @Nullable TransactionConfig definition) {
    JdbcConnection connection = beginTransaction(definition);
    V result;
    try {
      result = runnable.run(connection, argument);
    }
    catch (Throwable ex) {
      connection.rollback();
      if (ex instanceof DataAccessException e) {
        throw e;
      }
      else if (ex instanceof SQLException e) {
        throw translateException("Running in transaction", null, e);
      }
      throw new PersistenceException(
              "An error occurred while executing ResultStatementRunnable. Transaction rolled back.", ex);
    }
    connection.commit();
    return result;
  }

  //

  <T> TypeHandler<T> getTypeHandler(BeanProperty property) {
    return typeHandlerManager.getTypeHandler(property);
  }

  <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return typeHandlerManager.getTypeHandler(type);
  }

  // MapperProvider

  @Override
  public <T> T getMapper(Class<T> mapperClass) {
    return mapperFactory.getMapper(mapperClass);
  }

}
