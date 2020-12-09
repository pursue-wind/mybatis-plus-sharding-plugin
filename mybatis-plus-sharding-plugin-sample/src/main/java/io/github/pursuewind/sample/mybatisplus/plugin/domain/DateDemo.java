package io.github.pursuewind.sample.mybatisplus.plugin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Chan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "date_demo")
public class DateDemo implements Serializable {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    /**
     * name
     */
    @TableField(value = "name")
    private String name;

//    /**
//     * name
//     */
//    @TableField(value = "create_time")
//    private Date createTime;
    /**
     * name
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}