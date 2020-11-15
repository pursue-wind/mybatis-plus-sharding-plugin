package io.github.pursuewind.sample.mybatisplus.plugin.test;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.pursuewind.mybatisplus.plugin.support.ShardingStrategy;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.DateDemo;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.Person;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.User;
import io.github.pursuewind.sample.mybatisplus.plugin.mapper.DateDemoMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.mapper.PersonMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.mapper.UserMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestCase {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PersonMapper personMapper;
    @Autowired
    private DateDemoMapper dateDemoMapper;

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


    @Test
    public void userMapperTest() {
        Stream.iterate(0, x -> new Random().nextInt(99))
                .limit(10)
                .forEach(i -> {
                    //根据分表字段自动查找
                    User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, i));
                    System.err.println(user);
                });

        //根据分表字段自动查找
        User user = userMapper.selectById(23);
        System.out.println(user);

        User user2 = userMapper.selectOne(
                Wrappers.<User>query()
                        // 根据固定参数传入表名查找
                        .eq(ShardingStrategy.TABLE_NAME, "user_2")
                        .eq("name", "name_22")
        );
        System.out.println(user2);
    }

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

        // 分表字段涉及两个时无法使用单参数查询方法，必须保证sql中含有分表相关的字段
        Person person = personMapper.selectById(23);
    }

    @Test
    public void dateDemoMapperTest() {
        //根据分表字段自动查找
        DateDemo dateDemo = dateDemoMapper.selectOne(
                Wrappers.<DateDemo>lambdaQuery()
                        .eq(DateDemo::getId, 23));
        System.out.println(dateDemo);
    }
}
