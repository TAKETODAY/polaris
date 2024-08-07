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

import org.junit.jupiter.api.TestInstance;

import cn.taketoday.polaris.AbstractRepositoryManagerTests;
import cn.taketoday.polaris.DefaultEntityManager;
import cn.taketoday.polaris.jdbc.NamedQuery;
import cn.taketoday.polaris.jdbc.RepositoryManager;
import cn.taketoday.polaris.model.UserModel;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2024/5/24 16:12
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapperFactoryTests extends AbstractRepositoryManagerTests {

  @Override
  protected void prepareTestsData(DbType dbType, RepositoryManager repositoryManager) {
    try (NamedQuery query = repositoryManager.createNamedQuery("""
            drop table if exists t_user;
            create table t_user
            (
                `id`               int auto_increment primary key,
                `age`              int           default 0    comment 'Age',
                `name`             varchar(255)  default null comment '用户名',
                `avatar`           mediumtext    default null comment '头像',
                `password`         varchar(255)  default null comment '密码',
                `introduce`        varchar(1000) default null comment '介绍',
                `email`            varchar(255)  default null comment 'email',
                `gender`           int           default -1   comment '性别',
                `mobile_phone`     varchar(36)   default null comment '手机号'
            );
            """)) {

      query.executeUpdate();
    }
  }

  @ParameterizedRepositoryManagerTest
  void queryMapper(RepositoryManager repositoryManager) {
    DefaultEntityManager entityManager = new DefaultEntityManager(repositoryManager);
    entityManager.persist(UserModel.male("today", 1));
    MapperFactory mapperFactory = new MapperFactory(repositoryManager);

    UserMapper userMapper = mapperFactory.getMapper(UserMapper.class);

    UserModel byId = userMapper.findById(1);
    System.out.println(byId);
  }

}
