package io.github.pursuewind.sample.mybatisplus.plugin.test;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.DateDemo;
import io.github.pursuewind.sample.mybatisplus.plugin.domain.User;
import io.github.pursuewind.sample.mybatisplus.plugin.mapper.DateDemoMapper;
import io.github.pursuewind.sample.mybatisplus.plugin.mapper.UserMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestCase {
    @Autowired
    private UserMapper userMapper;
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
                    dateDemoMapper.insert(DateDemo.builder().id(i).name("name" + i).createTime(LocalDateTime.now()).build());
                });
    }

    @Test
    public void testCRUD() {
//        userMapper.insert(User.builder().id(2).name("name" + 2).build());

        User id = userMapper.selectOne(Wrappers.<User>query().eq("id", 2).eq("name", "name2"));

        System.out.println(id);

        userMapper.update(
                User.builder().name("name233").build(),
                Wrappers.<User>update().eq("id", 2)
        );

        User id2 = userMapper.selectOne(Wrappers.<User>query().eq("id", 2));

        System.out.println(id2);

        userMapper.delete(Wrappers.<User>update().eq("id", 2));
    }

    @Test
    public void testCRUD2() {
        userMapper.deleteById(2);
        userMapper.insert(User.builder().id(2).name("name" + 2).build());

        User user = userMapper.selectById(2);
        userMapper.deleteById(2);
        User user2 = userMapper.selectById(2);
        userMapper.insert(User.builder().id(2).name("name" + 2).build());
        userMapper.update(User.builder().name("2333").build(), Wrappers.<User>update().eq("id", 2));
        userMapper.delete(Wrappers.<User>query().eq("id", 2));
//        User user = userMapper.selectByIdAndName(2,"name");
//        User user =
//        System.out.println(user);

    }

    @Test
    public void testIn() {

        userMapper.selectList(Wrappers.<User>query().in("xx_id", 2, 3, 4));

    }

    @Test
    public void userMapperTest() {
//        Stream.iterate(0, x -> new Random().nextInt(99))
//                .limit(10)
//                .forEach(i -> {
//                    //根据分表字段自动查找
//                    User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, i));
//                    System.err.println(user);
//                });

        User user4 = userMapper.selectOne(Wrappers.<User>query().eq("id", 22).in("id", 23, 24, 25));
        System.err.println(user4);

        //根据分表字段自动查找，sql：SELECT id,name FROM user_3 WHERE id=?
        User user = userMapper.selectById(23);
        System.out.println(user);


        User user3 = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, 22));
        System.err.println(user3);
    }


    @Test
    public void dateDemoMapperTest() {
        // 有时间参数会根据时间参数找到表插入
        // sql：INSERT INTO date_demo_2020_11 ( id, name ) VALUES ( ?, ? )
        int ceshi1 = dateDemoMapper.insert(
                DateDemo.builder().id(239).name("ceshi").createTime(LocalDateTime.now()).build()
        );

        //根据分表字段自动查找
        // sql: SELECT id,name,create_time FROM date_demo_2020_11 WHERE (create_time = ?)
//        DateDemo dateDemo = dateDemoMapper.selectOne(
//                Wrappers.<DateDemo>lambdaQuery()
//                        .eq(DateDemo::getCreateTime, LocalDateTime.now()));
//
//        System.out.println(dateDemo);
    }
}
