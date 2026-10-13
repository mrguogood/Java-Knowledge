-- =====================================================================
-- 数据库脚本
-- 说明：
--   1. 创建 java_knowledge 数据库
--   2. 创建知识库文件管理表 knowledge_file
--   3. 上传的 pdf / word / excel 文件元数据存于此表
--      文件本体存放在项目根目录 uploadFiles 下
-- 使用：直接在 MySQL 客户端或 Navicat 中执行即可
-- =====================================================================

-- 1. 创建数据库（不存在才创建）
CREATE DATABASE IF NOT EXISTS `java_knowledge`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `java_knowledge`;

-- 2. 知识库文件管理表
DROP TABLE IF EXISTS `knowledge_file`;

CREATE TABLE `knowledge_file` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title`       VARCHAR(200) NOT NULL                COMMENT '知识标题/显示名称',
    `category`    VARCHAR(50)  DEFAULT '其他'           COMMENT '分类',
    `author`      VARCHAR(50)  DEFAULT ''              COMMENT '作者/上传人',
    `status`      VARCHAR(20)  DEFAULT '草稿'           COMMENT '状态：草稿 / 已发布',
    `view_count`  INT          DEFAULT 0               COMMENT '浏览量',
    `file_type`   VARCHAR(20)  DEFAULT ''              COMMENT '文件类型：pdf / word / excel',
    `file_name`   VARCHAR(255) DEFAULT ''              COMMENT '原始文件名',
    `stored_name` VARCHAR(255) DEFAULT ''              COMMENT '存储文件名（UUID）',
    `file_path`   VARCHAR(500) DEFAULT ''              COMMENT '文件相对路径（uploadFiles/xxx.ext）',
    `file_size`   BIGINT       DEFAULT 0               COMMENT '文件大小（字节）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP     COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_title` (`title`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文件管理表';

-- 3. 可选的初始演示数据（方便第一时间看到列表效果）
INSERT INTO `knowledge_file`
    (`title`, `category`, `author`, `status`, `view_count`, `file_type`, `file_name`, `file_path`, `file_size`)
VALUES
    ('Java 并发编程实战.pdf',   'Java',   '张三', '已发布', 0, 'pdf',   'Java并发编程实战.pdf',   'uploadFiles/demo-java.pdf',   2048),
    ('Spring Boot 入门指南.docx', '框架',  '李四', '已发布', 0, 'word',  'SpringBoot入门指南.docx', 'uploadFiles/demo-spring.docx', 4096),
    ('MySQL 优化.xlsx',         '数据库', '王五', '草稿',   0, 'excel', 'MySQL优化.xlsx',         'uploadFiles/demo-mysql.xlsx',  1024);