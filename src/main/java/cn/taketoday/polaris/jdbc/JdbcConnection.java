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

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;

import javax.sql.DataSource;

import cn.taketoday.dao.DataAccessException;
import cn.taketoday.dao.InvalidDataAccessApiUsageException;
import cn.taketoday.jdbc.datasource.DataSourceUtils;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.transaction.HeuristicCompletionException;
import cn.taketoday.transaction.IllegalTransactionStateException;
import cn.taketoday.transaction.TransactionDefinition;
import cn.taketoday.transaction.TransactionException;
import cn.taketoday.transaction.TransactionStatus;
import cn.taketoday.transaction.TransactionSystemException;
import cn.taketoday.transaction.UnexpectedRollbackException;

/**
 * Represents a connection to the database with a transaction.
 */
public final class JdbcConnection implements Closeable, QueryProducer {
  private static final Logger log = LoggerFactory.getLogger(JdbcConnection.class);

  private final RepositoryManager manager;
  private final DataSource dataSource;

  @Nullable
  private Connection root;

  final boolean autoClose;

  private boolean rollbackOnClose = true;
  private boolean rollbackOnException = true;

  private final HashSet<Statement> statements = new HashSet<>();

  @Nullable
  private TransactionStatus transaction;

  public JdbcConnection(RepositoryManager manager, DataSource dataSource, boolean autoClose) {
    this.manager = manager;
    this.autoClose = autoClose;
    this.dataSource = dataSource;
    createConnection();
  }

  public JdbcConnection(RepositoryManager manager, DataSource dataSource) {
    this.manager = manager;
    this.autoClose = false;
    this.dataSource = dataSource;
  }

  void onException() {
    if (rollbackOnException) {
      rollback(autoClose);
    }
  }

  /**
   * @throws DataAccessException Could not acquire a connection from data-source
   * @see DataSource#getConnection()
   * @since 1.0
   */
  @Override
  public Query createQuery(String queryText) {
    boolean returnGeneratedKeys = manager.isGeneratedKeys();
    return createQuery(queryText, returnGeneratedKeys);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   * @see DataSource#getConnection()
   * @since 1.0
   */
  @Override
  public Query createQuery(String queryText, boolean returnGeneratedKeys) {
    createConnectionIfNecessary();
    return new Query(this, queryText, returnGeneratedKeys);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   * @see DataSource#getConnection()
   * @since 1.0
   */
  public Query createQuery(String queryText, String... columnNames) {
    createConnectionIfNecessary();
    return new Query(this, queryText, columnNames);
  }

  /**
   * @throws DataAccessException Could not acquire a connection from data-source
   * @see DataSource#getConnection()
   */
  @Override
  public NamedQuery createNamedQuery(String queryText) {
    boolean returnGeneratedKeys = manager.isGeneratedKeys();
    return createNamedQuery(queryText, returnGeneratedKeys);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   * @see DataSource#getConnection()
   */
  @Override
  public NamedQuery createNamedQuery(String queryText, boolean returnGeneratedKeys) {
    createConnectionIfNecessary();
    return new NamedQuery(this, queryText, returnGeneratedKeys);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   * @see DataSource#getConnection()
   */
  public NamedQuery createNamedQuery(String queryText, String... columnNames) {
    createConnectionIfNecessary();
    return new NamedQuery(this, queryText, columnNames);
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   * @see DataSource#getConnection()
   */
  private void createConnectionIfNecessary() {
    try {
      if (root == null || root.isClosed()) {
        createConnection();
      }
    }
    catch (SQLException e) {
      throw translateException("Retrieves Connection status is closed", e);
    }
  }

  /**
   * use :p1, :p2, :p3 as the parameter name
   */
  public NamedQuery createNamedQueryWithParams(String queryText, Object... paramValues) {
    // due to #146, creating a query will not create a statement anymore
    // the PreparedStatement will only be created once the query needs to be executed
    // => there is no need to handle the query closing here anymore since there is nothing to close
    return createNamedQuery(queryText)
            .withParams(paramValues);
  }

  /**
   * Return a currently active transaction or create a new one, according to
   * the specified propagation behavior.
   * <p>Note that parameters like isolation level or timeout will only be applied
   * to new transactions, and thus be ignored when participating in active ones.
   * <p>Furthermore, not all transaction definition settings will be supported
   * by every transaction manager: A proper transaction manager implementation
   * should throw an exception when unsupported settings are encountered.
   * <p>An exception to the above rule is the read-only flag, which should be
   * ignored if no explicit read-only mode is supported. Essentially, the
   * read-only flag is just a hint for potential optimization.
   *
   * @return transaction status object representing the new or current transaction
   * @throws TransactionException in case of lookup, creation, or system errors
   * @throws IllegalTransactionStateException if the given transaction definition
   * cannot be executed (for example, if a currently active transaction is in
   * conflict with the specified propagation behavior)
   * @see TransactionDefinition#getPropagationBehavior
   * @see TransactionDefinition#getIsolationLevel
   * @see TransactionDefinition#getTimeout
   * @see TransactionDefinition#isReadOnly
   */
  public TransactionStatus beginTransaction() {
    return beginTransaction(TransactionDefinition.withDefaults());
  }

  /**
   * Return a currently active transaction or create a new one, according to
   * the specified propagation behavior.
   * <p>Note that parameters like isolation level or timeout will only be applied
   * to new transactions, and thus be ignored when participating in active ones.
   * <p>Furthermore, not all transaction definition settings will be supported
   * by every transaction manager: A proper transaction manager implementation
   * should throw an exception when unsupported settings are encountered.
   * <p>An exception to the above rule is the read-only flag, which should be
   * ignored if no explicit read-only mode is supported. Essentially, the
   * read-only flag is just a hint for potential optimization.
   *
   * @param definition the TransactionDefinition instance (can be {@code null} for defaults),
   * describing propagation behavior, isolation level, timeout etc.
   * @return transaction status object representing the new or current transaction
   * @throws TransactionException in case of lookup, creation, or system errors
   * @throws IllegalTransactionStateException if the given transaction definition
   * cannot be executed (for example, if a currently active transaction is in
   * conflict with the specified propagation behavior)
   * @see TransactionDefinition#getPropagationBehavior
   * @see TransactionDefinition#getIsolationLevel
   * @see TransactionDefinition#getTimeout
   * @see TransactionDefinition#isReadOnly
   */
  public TransactionStatus beginTransaction(@Nullable TransactionDefinition definition) {
    if (transaction != null) {
      throw new InvalidDataAccessApiUsageException("Transaction require commit or rollback");
    }
    setRollbackOnClose(false);
    return this.transaction = manager.getTransactionManager().getTransaction(definition);
  }

  @Nullable
  public TransactionStatus getTransaction() {
    return transaction;
  }

  /**
   * Undoes all changes made in the current transaction
   * and releases any database locks currently held
   * by this <code>Connection</code> object. This method should be
   * used only when auto-commit mode has been disabled.
   *
   * @throws DataAccessException if a database access error occurs,
   * this method is called while participating in a distributed transaction,
   * this method is called on a closed connection or this
   * <code>Connection</code> object is in auto-commit mode
   * @throws TransactionSystemException in case of rollback or system errors
   * (typically caused by fundamental resource failures)
   * @throws IllegalTransactionStateException if the given transaction
   * is already completed (that is, committed or rolled back)
   */
  public RepositoryManager rollback() {
    rollback(true);
    return manager;
  }

  /**
   * Undoes all changes made in the current transaction
   * and releases any database locks currently held
   * by this <code>Connection</code> object. This method should be
   * used only when auto-commit mode has been disabled.
   *
   * @throws DataAccessException if a database access error occurs,
   * this method is called while participating in a distributed transaction,
   * this method is called on a closed connection or this
   * <code>Connection</code> object is in auto-commit mode
   * @throws TransactionSystemException in case of rollback or system errors
   * (typically caused by fundamental resource failures)
   * @throws IllegalTransactionStateException if the given transaction
   * is already completed (that is, committed or rolled back)
   */
  public JdbcConnection rollback(boolean closeConnection) {
    if (transaction != null) {
      manager.getTransactionManager().rollback(transaction);
    }
    if (closeConnection) {
      closeConnection();
    }

    this.transaction = null;
    return this;
  }

  /**
   * Makes all changes made since the previous
   * commit/rollback permanent and releases any database locks
   * currently held by this <code>Connection</code> object.
   * This method should be
   * used only when auto-commit mode has been disabled.
   *
   * @throws DataAccessException if a database access error occurs,
   * this method is called while participating in a distributed transaction,
   * if this method is called on a closed connection or this
   * <code>Connection</code> object is in auto-commit mode
   */
  public void commit() {
    commit(true);
  }

  /**
   * Makes all changes made since the previous
   * commit/rollback permanent and releases any database locks
   * currently held by this <code>Connection</code> object.
   * This method should be
   * used only when auto-commit mode has been disabled.
   *
   * @param closeConnection close connection
   * @throws DataAccessException if a database access error occurs,
   * this method is called while participating in a distributed transaction,
   * if this method is called on a closed connection or this
   * <code>Connection</code> object is in auto-commit mode
   * @throws UnexpectedRollbackException in case of an unexpected rollback
   * that the transaction coordinator initiated
   * @throws HeuristicCompletionException in case of a transaction failure
   * caused by a heuristic decision on the side of the transaction coordinator
   * @throws TransactionSystemException in case of commit or system errors
   * (typically caused by fundamental resource failures)
   * @throws IllegalTransactionStateException if the given transaction
   * is already completed (that is, committed or rolled back)
   * @see TransactionStatus#setRollbackOnly
   */
  public void commit(boolean closeConnection) {
    if (transaction != null) {
      manager.getTransactionManager().commit(transaction);
    }
    if (closeConnection) {
      closeConnection();
    }
    this.transaction = null;
  }

  void registerStatement(Statement statement) {
    statements.add(statement);
  }

  void removeStatement(Statement statement) {
    statements.remove(statement);
  }

  // Closeable

  @Override
  public void close() {
    boolean connectionIsClosed;
    try {
      connectionIsClosed = root != null && root.isClosed();
    }
    catch (SQLException e) {
      throw translateException("trying to determine whether the connection is closed.", e);
    }

    if (!connectionIsClosed) {
      for (Statement statement : statements) {
        try {
          statement.close();
        }
        catch (SQLException ex) {
          if (manager.isCatchResourceCloseErrors()) {
            throw translateException("Trying to close statement", ex);
          }
          else {
            log.warn("Could not close statement. statement: {}", statement, ex);
          }
        }
      }
      statements.clear();

      boolean rollback = rollbackOnClose;
      if (rollback) {
        try {
          rollback = !root.getAutoCommit();
        }
        catch (SQLException e) {
          log.warn("Could not determine connection auto commit mode.", e);
        }
      }

      // if in transaction, rollback, otherwise just close
      if (rollback) {
        rollback(true);
      }
      else {
        closeConnection();
      }
    }
  }

  /**
   * @throws CannotGetJdbcConnectionException Could not acquire a connection from connection-source
   */
  void createConnection() {
    this.root = DataSourceUtils.getConnection(dataSource);
  }

  private void closeConnection() {
    if (transaction != null || DataSourceUtils.isConnectionTransactional(root, dataSource)) {
      DataSourceUtils.releaseConnection(root, dataSource);
    }
    else {
      try {
        root.close();
      }
      catch (SQLException ex) {
        if (manager.isCatchResourceCloseErrors()) {
          throw translateException("Trying to close connection", ex);
        }
        else {
          log.warn("Could not close connection: {}", root, ex);
        }
      }
    }
  }

  //
  public boolean isRollbackOnException() {
    return rollbackOnException;
  }

  public void setRollbackOnException(boolean rollbackOnException) {
    this.rollbackOnException = rollbackOnException;
  }

  public boolean isRollbackOnClose() {
    return rollbackOnClose;
  }

  public void setRollbackOnClose(boolean rollbackOnClose) {
    this.rollbackOnClose = rollbackOnClose;
  }

  public Connection getJdbcConnection() {
    return root;
  }

  public RepositoryManager getManager() {
    return manager;
  }

  private DataAccessException translateException(String task, SQLException ex) {
    return manager.translateException(task, null, ex);
  }

}
