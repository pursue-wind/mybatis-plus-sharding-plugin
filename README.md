# mybatis-plus-sharding-plugin
> MyBatisPlus 分表插件，兼容条件构造器
### 使用
#### 引入依赖
```xml
<dependency>
    <groupId>io.github.mirrormingzz</groupId>
    <artifactId>mybatis-plus-sharding-plugin</artifactId>
    <version>0.3</version>
</dependency>
```

#### 使用  参考 mybatis-plus-sharding-plugin-sample 内代码
```java
    @Bean
    public TableShardingInterceptor mybatisPlusShardingPlugin() {
        return new TableShardingInterceptor();
    }
```
```java
// 在Mapper上加上注解
@TableSharding(tableName = "person", paramName = "id", prefixParam = "type", strategy = PersonShardingStrategy.class)
    tableName       原表名
    paramName       分表后缀处理参数对应实体类中的字段
    prefixParam     前缀参数对应实体类中的字段
    strategy        分表策略 实现 ShardingStrategy 接口
```
```java
/**
 * 插入测试，初始化数据
 */
@Test
public void initDBData() {
    IntStream.range(1, 99)
            .forEach(i -> {
                userMapper.insert(User.builder().id(i).name("name" + i).build());
            });

    IntStream.range(1, 99)
            .forEach(i -> {
                int type = (i & 1) == 0 ? 1 : 2;
                personMapper.insert(Person.builder().id(i).name("name" + i).type("type" + type).build());
            });
    IntStream.range(1, 99)
            .forEach(i -> {
                dateDemoMapper.insert(DateDemo.builder().id(i).name("name" + i).build());
            });
}
```

```java
@Test
public void userMapperTest() {
    Stream.iterate(0, x -> new Random().nextInt(99))
            .limit(10)
            .forEach(i -> {
                //根据分表字段自动查找
                User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, i));
                System.err.println(user);
            });

    //根据分表字段自动查找，sql：SELECT id,name FROM user_3 WHERE id=?
    User user = userMapper.selectById(23);
    System.out.println(user);

    // 根据固定参数 ShardingStrategy.TABLE_NAME 传入表名查找，sql：SELECT id,name FROM user_2 WHERE ('user_2' = 'user_2' AND name = 'name_22')
    User user2 = userMapper.selectOne(
            Wrappers.<User>query()
                    .eq(ShardingStrategy.TABLE_NAME, "user_2")
                    .eq("name", "name_22")
    );
    System.out.println(user2);
}
```

#### 注：分表字段涉及两个时无法使用单参数查询方法，必须保证sql中含有分表相关的字段，否则会抛出异常
```java  
@Test
public void personMapperTest() {
    Stream.iterate(0, x -> new Random().nextInt(99))
            .limit(10)
            .forEach(i -> {
                int type = (i & 1) == 0 ? 1 : 2;
                //根据分表字段自动查找
                Person person = personMapper.selectOne(
                        Wrappers.<Person>lambdaQuery()
                                .eq(Person::getId, i)
                                .eq(Person::getType, "type" + type));
                System.err.println(person);
            });

    // 分表字段涉及两个时无法使用单参数查询方法，必须保证sql中含有分表相关的字段，抛出异常
    Person person = personMapper.selectById(23);
}
```

```java  
@Test
public void dateDemoMapperTest() {
    // 有时间参数会根据时间参数找到表插入
    // sql：INSERT INTO date_demo_2020_11 ( id, name ) VALUES ( ?, ? )
    int ceshi1 = dateDemoMapper.insert(
            DateDemo.builder().id(233).name("ceshi").createTime(LocalDate.now()).build()
    );
    // 没有时间参数会根据当前时间找到表插入
    // sql：INSERT INTO date_demo_2020_11 ( id, name ) VALUES ( ?, ? )
    int ceshi2 = dateDemoMapper.insert(
            DateDemo.builder().id(234).name("ceshi").build()
    );
    //根据分表字段自动查找
    // sql: SELECT id,name,create_time FROM date_demo_2020_11 WHERE (create_time = ?)
    DateDemo dateDemo = dateDemoMapper.selectOne(
            Wrappers.<DateDemo>lambdaQuery()
                    .eq(DateDemo::getCreateTime, LocalDate.now()));

    System.out.println(dateDemo);
}
```

