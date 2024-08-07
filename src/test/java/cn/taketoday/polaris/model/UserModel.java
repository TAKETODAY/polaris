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

package cn.taketoday.polaris.model;

import java.util.Objects;

import cn.taketoday.polaris.annotation.Id;
import cn.taketoday.polaris.annotation.Table;
import lombok.Data;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 1.0 2022/8/16 22:57
 */
@Data
@Table("t_user")
public class UserModel {

  @Id
  public Integer id;

  public Integer age;

  public String name;
  public String avatar;
  public String password;
  public String introduce;
  public String mobilePhone;
  public String email;

  public Gender gender;

  public UserModel() { }

  public UserModel(String name, Gender gender, int age) {
    this.age = age;
    this.name = name;
    this.gender = gender;
  }

  public static UserModel male(String name, int age) {
    UserModel userModel = new UserModel();
    userModel.name = name;
    userModel.gender = Gender.MALE;
    userModel.age = age;
    return userModel;
  }

  public static UserModel forId(int id) {
    UserModel userModel = new UserModel();
    userModel.id = id;
    return userModel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof UserModel userModel))
      return false;
    return Objects.equals(age, userModel.age)
            && gender == userModel.gender
            && Objects.equals(id, userModel.id)
            && Objects.equals(name, userModel.name)
            && Objects.equals(email, userModel.email)
            && Objects.equals(avatar, userModel.avatar)
            && Objects.equals(password, userModel.password)
            && Objects.equals(introduce, userModel.introduce)
            && Objects.equals(mobilePhone, userModel.mobilePhone);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, age, name, avatar, password, introduce, mobilePhone, email, gender);
  }

}
