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

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import cn.taketoday.polaris.annotation.GeneratedId;
import cn.taketoday.polaris.annotation.Id;
import cn.taketoday.polaris.beans.BeanMetadata;
import cn.taketoday.polaris.beans.BeanProperty;
import cn.taketoday.polaris.model.UserModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/18 16:55
 */
class DefaultEntityMetadataFactoryTests {
  DefaultEntityMetadataFactory factory = new DefaultEntityMetadataFactory();

  @Test
  void defaultState() {
    EntityMetadata entityMetadata = factory.createEntityMetadata(UserModel.class);
    assertThat(entityMetadata).isNotNull();
    assertThatThrownBy(() ->
            factory.createEntityMetadata(Object.class))
            .isInstanceOf(IllegalEntityException.class)
            .hasMessageStartingWith("Cannot determine properties for entity: " + Object.class);
  }

  @Test
  void exception() {
    assertIllegalArgumentException(factory::setPropertyFilter);
    assertIllegalArgumentException(factory::setColumnNameDiscover);
    assertIllegalArgumentException(factory::setIdPropertyDiscover);
    assertIllegalArgumentException(factory::setTableNameGenerator);

    factory.setTableNameGenerator(TableNameGenerator.forTableAnnotation());
    assertThatThrownBy(() ->
            factory.createEntityMetadata(Object.class))
            .isInstanceOf(IllegalEntityException.class)
            .hasMessage("Cannot determine table name for entity: " + Object.class);

    factory.setTableNameGenerator(
            TableNameGenerator.forTableAnnotation()
                    .and(TableNameGenerator.defaultStrategy())
    );

    factory.setColumnNameDiscover(ColumnNameDiscover.forColumnAnnotation());

    assertThatThrownBy(() ->
            factory.createEntityMetadata(UserModel.class))
            .isInstanceOf(IllegalEntityException.class)
            .hasMessageStartingWith("Cannot determine column name for property: UserModel#");

    factory.setPropertyFilter(PropertyFilter.acceptAny());
  }

  @Test
  void multipleId() {
    class MultipleId extends UserModel {
      @Id
      private Long id_;
    }
    assertThatThrownBy(() ->
            factory.createEntityMetadata(MultipleId.class))
            .isInstanceOf(IllegalEntityException.class)
            .hasMessage("Only one Id property supported, entity: " + MultipleId.class);
  }

  @Test
  void overrideId() {
    class OverrideId extends UserModel {
      @Id
      public Integer id;

    }

    EntityMetadata entityMetadata = factory.createEntityMetadata(OverrideId.class);
    assertThat(entityMetadata.idProperty).isNotNull();
    BeanProperty id = BeanMetadata.forClass(OverrideId.class).obtainBeanProperty("id");
    assertThat(entityMetadata.idProperty.property)
            .isEqualTo(id);
  }

  @Test
  void idDiscover() {
    class IdDiscover {
      private Long id;

      private Long id_;
    }

    // default

    EntityMetadata entityMetadata = factory.createEntityMetadata(IdDiscover.class);
    assertThat(entityMetadata.idProperty).isNotNull();
    assertThat(entityMetadata.idProperty.property)
            .isEqualTo(BeanProperty.valueOf(IdDiscover.class, "id"));

    factory.setIdPropertyDiscover(IdPropertyDiscover.forPropertyName("id_"));

    entityMetadata = factory.createEntityMetadata(IdDiscover.class);
    assertThat(entityMetadata.idProperty).isNotNull();
    assertThat(entityMetadata.idProperty()).isNotNull();
    assertThat(entityMetadata.idProperty.property)
            .isEqualTo(BeanProperty.valueOf(IdDiscover.class, "id_"));
  }

  @Test
  void getEntityHolder() {
    EntityMetadata entityMetadata = factory.getEntityMetadata(UserModel.class);
    assertThat(factory.entityCache.get(UserModel.class)).isEqualTo(entityMetadata);
  }

  @Test
  void nullId() {
    class NullId {
      String name;
    }
    EntityMetadata entityMetadata = factory.getEntityMetadata(NullId.class);
    assertThat(factory.entityCache.get(NullId.class)).isSameAs(entityMetadata);
    assertThat(entityMetadata.idProperty).isNull();
    assertThat(entityMetadata.idColumnName).isNull();
    assertThat(entityMetadata.autoGeneratedId).isFalse();

    assertThatThrownBy(entityMetadata::idProperty)
            .isInstanceOf(IllegalEntityException.class)
            .hasMessageStartingWith("ID property is required");
  }

  @Test
  void findProperty() {
    EntityMetadata entityMetadata = factory.getEntityMetadata(UserModel.class);
    assertThat(factory.entityCache.get(UserModel.class)).isEqualTo(entityMetadata);
    EntityProperty property = entityMetadata.findProperty("name");
    assertThat(property).isNotNull();
  }

  @Test
  void autoGeneratedId() {
    class AutoGeneratedId {

      Long id_;

      @GeneratedId
      public Long getId_() {
        return id_;
      }
    }

    var entityMetadata = factory.getEntityMetadata(AutoGeneratedId.class);
    assertThat(entityMetadata.idProperty).isNotNull();
    assertThat(entityMetadata.idColumnName).isNotNull().isEqualTo("id_");
    assertThat(entityMetadata.autoGeneratedId).isTrue();
  }

  @Test
  void myAnnoId() {
    class MyIdModel {

      Long id_;

      @MyId
      public Long getId_() {
        return id_;
      }
    }

    factory.setIdPropertyDiscover(IdPropertyDiscover.forAnnotation(MyId.class));
    var entityMetadata = factory.getEntityMetadata(MyIdModel.class);
    assertThat(entityMetadata.idProperty).isNotNull();
    assertThat(entityMetadata.idColumnName).isNotNull().isEqualTo("id_");
    assertThat(entityMetadata.autoGeneratedId).isFalse();
  }

  static <T> void assertIllegalArgumentException(Consumer<T> throwingCallable) {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> throwingCallable.accept(null))
            .withMessageEndingWith("is required");
  }

  @Target({ ElementType.FIELD, ElementType.METHOD })
  @Retention(RetentionPolicy.RUNTIME)
  public @interface MyId {

  }

}