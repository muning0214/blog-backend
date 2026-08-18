-- ============================================
-- Personal Blog Database Schema
-- MySQL 8.x
-- ============================================

CREATE DATABASE IF NOT EXISTS blog_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE blog_db;

-- -----------
-- users
-- -----------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `username`     VARCHAR(50)  NOT NULL COMMENT 'Login username',
  `password`     VARCHAR(100) NOT NULL COMMENT 'Encrypted password (bcrypt)',
  `nickname`    VARCHAR(50)   DEFAULT NULL COMMENT 'Display name',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT 'Email',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT 'Avatar URL',
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'ADMIN' COMMENT 'Role: ADMIN / USER',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0-disabled 1-enabled',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Users table';

-- -----------
-- categories
-- -----------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name`        VARCHAR(50) NOT NULL COMMENT 'Category name',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT 'Display order',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Categories table';

-- -----------
-- tags
-- -----------
DROP TABLE IF EXISTS `tags`;
CREATE TABLE `tags` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `name`        VARCHAR(50) NOT NULL COMMENT 'Tag name',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Tags table';

-- -----------
-- articles
-- -----------
DROP TABLE IF EXISTS `articles`;
CREATE TABLE `articles` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_id`       BIGINT        NOT NULL COMMENT 'Author user id',
  `category_id`  BIGINT        DEFAULT NULL COMMENT 'Category id',
  `title`        VARCHAR(200)  NOT NULL COMMENT 'Article title',
  `summary`      VARCHAR(500)  DEFAULT NULL COMMENT 'Article summary',
  `content`      LONGTEXT      NOT NULL COMMENT 'Article content (markdown/html)',
  `cover`        VARCHAR(255)  DEFAULT NULL COMMENT 'Cover image URL',
  `view_count`   BIGINT        NOT NULL DEFAULT 0 COMMENT 'View count',
  `status`       TINYINT       NOT NULL DEFAULT 1 COMMENT '0-draft 1-published',
  `is_top`       TINYINT       NOT NULL DEFAULT 0 COMMENT '1-pinned to top',
  `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  KEY `idx_article_user` (`user_id`),
  KEY `idx_article_category` (`category_id`),
  KEY `idx_article_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Articles table';

-- -----------
-- article_tags (many-to-many)
-- -----------
DROP TABLE IF EXISTS `article_tags`;
CREATE TABLE `article_tags` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `article_id`  BIGINT   NOT NULL COMMENT 'Article id',
  `tag_id`      BIGINT   NOT NULL COMMENT 'Tag id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_tag` (`article_id`, `tag_id`),
  KEY `idx_at_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Article-Tag relation table';

-- -----------
-- comments
-- -----------
DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `article_id`  BIGINT       NOT NULL COMMENT 'Article id',
  `user_id`     BIGINT       DEFAULT NULL COMMENT 'Commenter user id (nullable for guest)',
  `nickname`    VARCHAR(50)  NOT NULL COMMENT 'Commenter display name',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT 'Commenter email',
  `content`     VARCHAR(1000) NOT NULL COMMENT 'Comment content',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT 'Parent comment id, 0=top-level',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-pending 1-approved 2-rejected',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`),
  KEY `idx_comment_article` (`article_id`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Comments table';

-- ============================================
-- Seed data
-- ============================================

-- Default admin user. Password = "admin123" (BCrypt hash below)
INSERT INTO `users` (`username`, `password`, `nickname`, `email`, `role`, `status`)
VALUES ('admin', '$2a$10$N.ZOn9G6/YLFixA5mgOfUOR/iBvKqM5v6nXuGqMnKQqAvJ8nTjvX.', 'Administrator', 'admin@blog.com', 'ADMIN', 1);

-- Categories
INSERT INTO `categories` (`name`, `sort`) VALUES
('Technology', 1),
('Life', 2),
('Reading Notes', 3);

-- Tags
INSERT INTO `tags` (`name`) VALUES
('Spring Boot'),
('Vue'),
('MySQL'),
('Docker'),
('随笔');

-- Sample article (author = admin user id = 1, category = Technology id = 1)
INSERT INTO `articles` (`user_id`, `category_id`, `title`, `summary`, `content`, `cover`, `view_count`, `status`, `is_top`)
VALUES (1, 1, 'Welcome to my blog',
        'This is the first article of the blog system built with Spring Boot 3 + Vue 3.',
        '# Welcome\n\nThis blog system is built with **Spring Boot 3** and **Vue 3**.\n\nFeatures:\n\n- Article CRUD\n- Category & Tag management\n- Comment moderation\n- JWT authentication\n\nEnjoy!',
        'https://picsum.photos/seed/welcome/800/400', 0, 1, 1);

INSERT INTO `articles` (`user_id`, `category_id`, `title`, `summary`, `content`, `cover`, `view_count`, `status`, `is_top`)
VALUES (1, 1, 'Getting started with Spring Boot 3',
        'A quick guide to bootstrapping a Spring Boot 3 application.',
        '# Getting Started\n\nSpring Boot 3 requires **JDK 17+**.\n\n```xml\n<dependency>\n  <groupId>org.springframework.boot</groupId>\n  <artifactId>spring-boot-starter-web</artifactId>\n</dependency>\n```\n\nRun the application and visit `localhost:8080`.',
        'https://picsum.photos/seed/springboot/800/400', 0, 1, 0);

INSERT INTO `articles` (`user_id`, `category_id`, `title`, `summary`, `content`, `cover`, `view_count`, `status`, `is_top`)
VALUES (1, 2, 'My weekend in the mountains',
        'A short essay about a relaxing weekend getaway.',
        '# Weekend\n\nSometimes you just need to unplug and head to the mountains.\n\nFresh air, clear skies, and zero notifications.',
        'https://picsum.photos/seed/mountain/800/400', 0, 1, 0);

-- Article tags (article 1 -> Spring Boot, Vue; article 2 -> Spring Boot, MySQL; article 3 -> 随笔)
INSERT INTO `article_tags` (`article_id`, `tag_id`) VALUES
(1, 1), (1, 2),
(2, 1), (2, 3),
(3, 5);

-- Sample comments
INSERT INTO `comments` (`article_id`, `nickname`, `email`, `content`, `parent_id`, `status`)
VALUES (1, 'Guest', 'guest@example.com', 'Great first post! Looking forward to more.', 0, 1);

INSERT INTO `comments` (`article_id`, `nickname`, `email`, `content`, `parent_id`, `status`)
VALUES (1, 'Bob', 'bob@example.com', 'Can you share the source code?', 0, 0);
