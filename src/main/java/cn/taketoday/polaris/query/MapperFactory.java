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

package cn.taketoday.polaris.query;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.taketoday.polaris.jdbc.Query;
import cn.taketoday.polaris.jdbc.RepositoryManager;
import cn.taketoday.polaris.logging.Logger;
import cn.taketoday.polaris.logging.LoggerFactory;
import cn.taketoday.polaris.query.parsing.XMLParsingException;
import cn.taketoday.polaris.query.parsing.XNode;
import cn.taketoday.polaris.query.parsing.XPathParser;
import cn.taketoday.polaris.util.Assert;
import cn.taketoday.polaris.util.Nullable;
import cn.taketoday.polaris.util.StringUtils;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/5/24 16:13
 */
public class MapperFactory implements MapperProvider {

  private static final Logger log = LoggerFactory.getLogger(MapperFactory.class);

  public static final String DEFAULT_MAPPER_LOCATION = "mapper/mapper-config.xml";

  private final RepositoryManager repositoryManager;

  private final Set<String> loadedResource = new HashSet<>();

  // global SQL
  private final Map<String, String> sqlMap = new HashMap<>();

  /**
   * 全局的 XML SQL 配置
   */
  private String mapperLocation = DEFAULT_MAPPER_LOCATION;

  volatile boolean defaultLocationLoaded;

  public MapperFactory(RepositoryManager repositoryManager) {
    Assert.notNull(repositoryManager, "RepositoryManager is required");
    this.repositoryManager = repositoryManager;
  }

  /**
   * 设置全局的 XML 配置
   *
   * @param mapperLocation mapper 地址，从 classpath 下读取
   */
  public void setMapperLocation(@Nullable String mapperLocation) {
    this.mapperLocation = mapperLocation == null ? DEFAULT_MAPPER_LOCATION : mapperLocation;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> mapperClass) {
    loadDefaults();
    return (T) Proxy.newProxyInstance(mapperClass.getClassLoader(),
            new Class[] { mapperClass }, new MapperHandler(mapperClass));
  }

  private void loadDefaults() {
    if (defaultLocationLoaded) {
      return;
    }
    synchronized(loadedResource) {
      if (defaultLocationLoaded) {
        return;
      }
      defaultLocationLoaded = true;

      ClassLoader classLoader = MapperFactory.class.getClassLoader();
      new MapperLoader(classLoader, sqlMap, loadedResource).load(mapperLocation);
    }
  }

  Map<String, String> read(Class<?> mapperClass, String xmlResource) {
    Map<String, String> sqlMap = new LinkedHashMap<>(this.sqlMap);
    MapperLoader mapperLoader = new MapperLoader(mapperClass, sqlMap, loadedResource);
    mapperLoader.load(xmlResource);
    return new LinkedHashMap<>(sqlMap);
  }

  class MapperHandler implements InvocationHandler {

    private final Map<String, String> sqlMap;

    public MapperHandler(Class<?> mapperClass) {
      var annotation = mapperClass.getAnnotation(MapperLocation.class);
      if (annotation != null) {
        String resource = annotation.value();
        if (StringUtils.isBlank(resource)) {
          throw new IllegalArgumentException("Mapper location is empty on " + mapperClass);
        }
        sqlMap = Collections.unmodifiableMap(read(mapperClass, resource));
      }
      else {
        sqlMap = Collections.unmodifiableMap(MapperFactory.this.sqlMap);
      }
    }

    @Nullable
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      String statement = getStatement(method);

      if (statement != null) {
        return handleStatement(method, args, statement);
      }

      return null;
    }

    @Nullable
    private Object handleStatement(Method method, Object[] args, String statement) {
      Class<?> returnType = method.getReturnType();
      try (Query query = repositoryManager.createQuery(statement)) {
        for (Object arg : args) {
          query.addParameter(arg);
        }
        return query.iterate(returnType).first();
      }
    }

    @Nullable
    private String getStatement(Method method) {
      String statementId = getStatementId(method);
      String statement = sqlMap.get(statementId);
      if (statement == null) {
        String name = method.getDeclaringClass().getName();
        statementId = "%s.%s".formatted(name, method.getName());
        statement = sqlMap.get(statementId);
      }
      return statement;
    }

    private String getStatementId(Method method) {
      StatementId statementId = method.getAnnotation(StatementId.class);
      if (statementId != null) {
        String resource = statementId.value();
        if (StringUtils.isBlank(resource)) {
          throw new IllegalArgumentException("@StatementId is empty on " + method);
        }
        return resource;
      }
      return method.getName();
    }

  }

  static class MapperLoader {

    private final ClassLoader classLoader;

    private final Set<String> loadedResource;

    @Nullable
    private final Class<?> mapperClass;

    private final Map<String, String> sqlMap;

    MapperLoader(Class<?> mapperClass, Map<String, String> sqlMap, Set<String> loadedResource) {
      this.mapperClass = mapperClass;
      this.sqlMap = sqlMap;
      this.classLoader = mapperClass.getClassLoader();
      this.loadedResource = loadedResource;
    }

    MapperLoader(ClassLoader classLoader, Map<String, String> sqlMap, Set<String> loadedResource) {
      this.mapperClass = null;
      this.sqlMap = sqlMap;
      this.classLoader = classLoader;
      this.loadedResource = loadedResource;
    }

    public void load(String xmlResource) throws XMLParsingException {
      if (loadedResource.add(xmlResource)) {
        try (InputStream inputStream = getResourceAsStream(xmlResource)) {
          XPathParser parser = new XPathParser(inputStream, false);
          XNode mapperNode = parser.evalNode("/mapper");
          Assert.state(mapperNode != null, "Mapper node is required");
          String namespace = mapperNode.getStringAttribute("namespace");
          List<XNode> sqlNodes = parser.evalNodes("/mapper/sql");
          for (XNode sqlNode : sqlNodes) {
            String id = sqlNode.getStringAttribute("id");
            if (StringUtils.hasText(id)) {
              if (namespace != null) {
                id = "%s.%s".formatted(namespace, id);
              }
              String sqlBody = StringUtils.trimWhitespace(sqlNode.getStringBody());
              sqlMap.put(id, sqlBody);
            }
          }

          List<XNode> importNodes = parser.evalNodes("/mapper/import");
          for (XNode importNode : importNodes) {
            String importResource = importNode.getStringAttribute("resource");
            if (StringUtils.hasText(importResource)) {
              log.debug("Import resource: '{}'", importResource);
              load(importResource.trim());
            }
          }
        }
        catch (IOException e) {
          throw new XMLParsingException("Mapper resource read failed", e);
        }
      }
      else {
        throw new XMLParsingException("Mapper resource already loaded: " + xmlResource);
      }
    }

    private InputStream getResourceAsStream(String xmlResource) {
      InputStream resourceAsStream;
      if (mapperClass != null) {
        resourceAsStream = mapperClass.getResourceAsStream(xmlResource);
      }
      else {
        resourceAsStream = classLoader.getResourceAsStream(xmlResource);
      }
      if (resourceAsStream == null) {
        throw new IllegalStateException("Mapper resource: [%s] not found".formatted(xmlResource));
      }
      return resourceAsStream;
    }
  }

}
