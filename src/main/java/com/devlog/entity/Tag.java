package com.devlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签。
 *
 * <p>取代原云端设计里的 {@code tags TEXT[]} 数组列：拆成独立表之后，
 * 标签能挂颜色和描述，也能单独做统计与重命名。
 */
@Data
@TableName("tags")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者；0 表示系统预置 */
    private Long userId;

    private String name;

    /** 关联标识，唯一 */
    private String slug;

    private String description;

    private String color;

    @JsonIgnore
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
