-- Smart Queue Management System DB Setup
-- Run in phpMyAdmin after creating 'queue_system' DB

CREATE DATABASE IF NOT EXISTS `queue_system`
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `queue_system`;

-- Queue numbers table
CREATE TABLE IF NOT EXISTS `queues` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `queue_num` VARCHAR(10) NOT NULL UNIQUE,
  `service_code` VARCHAR(20) DEFAULT '',
  `window_num` INT DEFAULT 0,
  `gen_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,sx
  `called_time` TIMESTAMP NULL,
  `status` ENUM('waiting', 'next_batch', 'calling', 'served') DEFAULT 'waiting',
  INDEX `idx_status_time` (`status`, `gen_time`)
) ENGINE=InnoDB;

-- Migration: Add missing columns if they don't exist (for existing databases)
ALTER TABLE `queues` ADD COLUMN IF NOT EXISTS `service_code` VARCHAR(20) DEFAULT '';
ALTER TABLE `queues` ADD COLUMN IF NOT EXISTS `window_num` INT DEFAULT 0;

-- Organization settings (single row)
CREATE TABLE IF NOT EXISTS `organization_settings` (
  `id` INT PRIMARY KEY DEFAULT 1,
  `org_name` VARCHAR(200) NOT NULL DEFAULT 'Smart Queue Management System',
  `address` VARCHAR(300) DEFAULT '',
  `tagline` VARCHAR(200) DEFAULT 'Take a number • Wait • Be served',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CHECK (`id` = 1)
) ENGINE=InnoDB;

-- Services offered
CREATE TABLE IF NOT EXISTS `services` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `service_code` VARCHAR(20) NOT NULL UNIQUE,
  `service_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(300) DEFAULT '',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Windows / Counters configuration
CREATE TABLE IF NOT EXISTS `windows` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `window_number` INT NOT NULL UNIQUE,
  `window_name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(300) DEFAULT '',
  `service_ids` VARCHAR(200) DEFAULT '',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Insert default organization settings
INSERT IGNORE INTO `organization_settings` (`id`, `org_name`, `address`, `tagline`) VALUES
(1, 'Smart Queue Management System', '', 'Take a number • Wait • Be served');

-- Insert default services
INSERT IGNORE INTO `services` (`service_code`, `service_name`, `description`) VALUES
('PAY', 'Payments', 'Bill payments, fees, and financial transactions'),
('CON', 'Consultation', 'General inquiries and consultation services'),
('DOC', 'Document Request', 'Request for certificates, permits, and documents'),
('REG', 'Registration', 'New registrations and account creation');

-- Insert default windows
INSERT IGNORE INTO `windows` (`window_number`, `window_name`, `description`, `service_ids`) VALUES
(1, 'Window 1', 'Primary counter for Payments and Registration', 'PAY,REG'),
(2, 'Window 2', 'Consultation and general inquiries', 'CON'),
(3, 'Window 3', 'Document processing and requests', 'DOC');

-- Test data
INSERT IGNORE INTO `queues` (`queue_num`, `status`, `gen_time`) VALUES
('A001', 'served', '2024-10-01 08:30:00'),
('A002', 'served', '2024-10-01 08:32:00'),
('A013', 'waiting', '2024-10-01 09:25:00');

-- Clear queue fn (call when reset)
DELIMITER //
DROP PROCEDURE IF EXISTS `clear_waiting_queues`//
CREATE PROCEDURE `clear_waiting_queues`()
BEGIN
  DELETE FROM `queues` WHERE `status` IN ('waiting', 'next_batch');
END//
DELIMITER ;

