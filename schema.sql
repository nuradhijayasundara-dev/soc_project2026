-- ============================================================================
-- BACKHAUL-MATCH FLEET & COURIER MANAGEMENT SYSTEM
-- Full MySQL Schema — grouped by microservice
-- Each service should ideally own its own schema/database in production
-- (database-per-service). This combined file is for design/reference and
-- for local dev where all services share one MySQL instance.
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 3. LOCATION MASTER SERVICE  (created first — referenced by many services)
-- ============================================================================
CREATE TABLE Provinces (
    province_id     INT AUTO_INCREMENT PRIMARY KEY,
    province_name   VARCHAR(100) NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Districts (
    district_id     INT AUTO_INCREMENT PRIMARY KEY,
    province_id     INT NOT NULL,
    district_name   VARCHAR(100) NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (province_id) REFERENCES Provinces(province_id)
);

CREATE TABLE Cities (
    city_id         INT AUTO_INCREMENT PRIMARY KEY,
    district_id     INT NOT NULL,
    city_name       VARCHAR(100) NOT NULL,
    latitude        DECIMAL(10,7),
    longitude       DECIMAL(10,7),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (district_id) REFERENCES Districts(district_id)
);

-- ============================================================================
-- 1. AUTHENTICATION & AUTHORIZATION SERVICE
-- ============================================================================
CREATE TABLE Roles (
    role_id         INT AUTO_INCREMENT PRIMARY KEY,
    role_name       VARCHAR(50) NOT NULL UNIQUE,   -- e.g. ADMIN, FLEET_MANAGER, COURIER_MANAGER, DRIVER
    description     VARCHAR(255)
);

CREATE TABLE Permissions (
    permission_id   INT AUTO_INCREMENT PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL UNIQUE,  -- e.g. TRUCK_CREATE, BOOKING_APPROVE
    description     VARCHAR(255)
);

CREATE TABLE Role_Permissions (
    role_id         INT NOT NULL,
    permission_id   INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES Roles(role_id),
    FOREIGN KEY (permission_id) REFERENCES Permissions(permission_id)
);

CREATE TABLE Users (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    user_type       ENUM('ADMIN','FLEET_MANAGER','COURIER_MANAGER','DRIVER','CUSTOMER') NOT NULL,
    status          ENUM('ACTIVE','INACTIVE','SUSPENDED') DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE User_Roles (
    user_id         BIGINT NOT NULL,
    role_id         INT NOT NULL,
    assigned_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (role_id) REFERENCES Roles(role_id)
);

CREATE TABLE Login_History (
    login_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    login_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    ip_address      VARCHAR(45),
    device_info     VARCHAR(255),
    status          ENUM('SUCCESS','FAILED') NOT NULL,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

CREATE TABLE User_Sessions (
    session_id      VARCHAR(100) PRIMARY KEY,      -- JWT id / refresh token id
    user_id         BIGINT NOT NULL,
    issued_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME NOT NULL,
    revoked         BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

-- ============================================================================
-- 2. USER MANAGEMENT SERVICE
-- ============================================================================
CREATE TABLE User_Profiles (
    profile_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE,
    first_name      VARCHAR(80) NOT NULL,
    last_name       VARCHAR(80) NOT NULL,
    nic_number      VARCHAR(20),
    profile_image   VARCHAR(255),
    date_of_birth   DATE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

CREATE TABLE Addresses (
    address_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    address_line1   VARCHAR(150) NOT NULL,
    address_line2   VARCHAR(150),
    city_id         INT NOT NULL,
    postal_code     VARCHAR(15),
    is_default      BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Contacts (
    contact_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    phone_number    VARCHAR(20) NOT NULL,
    contact_type    ENUM('MOBILE','LANDLINE','EMERGENCY') DEFAULT 'MOBILE',
    is_primary      BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

-- ============================================================================
-- 4. COURIER MANAGEMENT SERVICE
-- ============================================================================
CREATE TABLE Courier_Companies (
    courier_company_id INT AUTO_INCREMENT PRIMARY KEY,
    company_name        VARCHAR(150) NOT NULL,
    registration_no      VARCHAR(50) UNIQUE,
    owner_user_id        BIGINT,
    status               ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES Users(user_id)
);

CREATE TABLE Courier_Branches (
    branch_id            INT AUTO_INCREMENT PRIMARY KEY,
    courier_company_id   INT NOT NULL,
    branch_name          VARCHAR(150) NOT NULL,
    city_id              INT NOT NULL,
    address              VARCHAR(255),
    contact_number       VARCHAR(20),
    FOREIGN KEY (courier_company_id) REFERENCES Courier_Companies(courier_company_id),
    FOREIGN KEY (city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Customers (
    customer_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    courier_company_id INT,
    business_name   VARCHAR(150),
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (courier_company_id) REFERENCES Courier_Companies(courier_company_id)
);

CREATE TABLE Receivers (
    receiver_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    receiver_name   VARCHAR(120) NOT NULL,
    phone_number    VARCHAR(20) NOT NULL,
    city_id         INT NOT NULL,
    address         VARCHAR(255) NOT NULL,
    FOREIGN KEY (city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Shipments (
    shipment_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_code     VARCHAR(30) NOT NULL UNIQUE,        -- e.g. BK-2024-123
    customer_id       BIGINT NOT NULL,
    receiver_id       BIGINT NOT NULL,
    courier_company_id INT NOT NULL,
    pickup_city_id    INT NOT NULL,
    delivery_city_id  INT NOT NULL,
    total_weight_kg   DECIMAL(10,2),
    status            ENUM('CREATED','ASSIGNED','IN_TRANSIT','DELIVERED','CANCELLED') DEFAULT 'CREATED',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES Customers(customer_id),
    FOREIGN KEY (receiver_id) REFERENCES Receivers(receiver_id),
    FOREIGN KEY (courier_company_id) REFERENCES Courier_Companies(courier_company_id),
    FOREIGN KEY (pickup_city_id) REFERENCES Cities(city_id),
    FOREIGN KEY (delivery_city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Shipment_Items (
    item_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT NOT NULL,
    item_description VARCHAR(150),
    quantity        INT DEFAULT 1,
    weight_kg       DECIMAL(10,2),
    FOREIGN KEY (shipment_id) REFERENCES Shipments(shipment_id)
);

CREATE TABLE Shipment_Tracking (
    tracking_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id     BIGINT NOT NULL,
    status          VARCHAR(50) NOT NULL,
    location        VARCHAR(150),
    remarks         VARCHAR(255),
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipment_id) REFERENCES Shipments(shipment_id)
);

-- ============================================================================
-- 5. FLEET MANAGEMENT SERVICE
-- ============================================================================
CREATE TABLE Fleet_Companies (
    fleet_company_id INT AUTO_INCREMENT PRIMARY KEY,
    company_name      VARCHAR(150) NOT NULL,
    registration_no   VARCHAR(50) UNIQUE,
    owner_user_id     BIGINT,
    status            ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES Users(user_id)
);

CREATE TABLE Trucks (
    truck_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    fleet_company_id  INT NOT NULL,
    truck_no          VARCHAR(20) NOT NULL UNIQUE,      -- e.g. WP-AB-1234
    truck_type        VARCHAR(50),
    capacity_ton      DECIMAL(6,2) NOT NULL,
    status            ENUM('AVAILABLE','IN_TRANSIT','MAINTENANCE','INACTIVE') DEFAULT 'AVAILABLE',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (fleet_company_id) REFERENCES Fleet_Companies(fleet_company_id)
);

CREATE TABLE Drivers (
    driver_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT NOT NULL UNIQUE,
    fleet_company_id  INT NOT NULL,
    status            ENUM('AVAILABLE','ON_TRIP','OFF_DUTY') DEFAULT 'AVAILABLE',
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (fleet_company_id) REFERENCES Fleet_Companies(fleet_company_id)
);

CREATE TABLE Driver_Licenses (
    license_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id       BIGINT NOT NULL,
    license_no      VARCHAR(30) NOT NULL UNIQUE,
    license_class   VARCHAR(20),
    issued_date     DATE,
    expiry_date     DATE NOT NULL,
    FOREIGN KEY (driver_id) REFERENCES Drivers(driver_id)
);

CREATE TABLE Truck_Availability (
    availability_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    truck_id          BIGINT NOT NULL,
    route_from_city_id INT NOT NULL,
    route_to_city_id   INT NOT NULL,
    available_capacity_ton DECIMAL(6,2) NOT NULL,
    available_from    DATETIME NOT NULL,
    available_to      DATETIME,
    status            ENUM('AVAILABLE','BOOKED','EXPIRED') DEFAULT 'AVAILABLE',
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id),
    FOREIGN KEY (route_from_city_id) REFERENCES Cities(city_id),
    FOREIGN KEY (route_to_city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Trips (
    trip_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    truck_id          BIGINT NOT NULL,
    driver_id         BIGINT NOT NULL,
    origin_city_id    INT NOT NULL,
    destination_city_id INT NOT NULL,
    start_time        DATETIME,
    end_time          DATETIME,
    status            ENUM('PLANNED','IN_TRANSIT','COMPLETED','CANCELLED') DEFAULT 'PLANNED',
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id),
    FOREIGN KEY (driver_id) REFERENCES Drivers(driver_id),
    FOREIGN KEY (origin_city_id) REFERENCES Cities(city_id),
    FOREIGN KEY (destination_city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Vehicle_Maintenance (
    maintenance_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    truck_id          BIGINT NOT NULL,
    maintenance_type  VARCHAR(100),
    scheduled_date    DATE,
    completed_date    DATE,
    cost              DECIMAL(10,2),
    remarks           VARCHAR(255),
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id)
);

-- ============================================================================
-- 6. GPS TRACKING SERVICE
-- ============================================================================
CREATE TABLE Live_GPS_Locations (
    truck_id        BIGINT PRIMARY KEY,     -- one live row per truck, upserted
    latitude        DECIMAL(10,7) NOT NULL,
    longitude       DECIMAL(10,7) NOT NULL,
    speed_kmh       DECIMAL(6,2),
    heading         DECIMAL(6,2),
    recorded_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id)
);

CREATE TABLE GPS_Tracking_History (
    history_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    truck_id        BIGINT NOT NULL,
    trip_id         BIGINT,
    latitude        DECIMAL(10,7) NOT NULL,
    longitude       DECIMAL(10,7) NOT NULL,
    recorded_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id),
    FOREIGN KEY (trip_id) REFERENCES Trips(trip_id)
);

-- ============================================================================
-- 7. MATCHING ENGINE SERVICE
-- ============================================================================
CREATE TABLE Match_Requests (
    match_request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id       BIGINT NOT NULL,
    requested_by      BIGINT NOT NULL,
    status            ENUM('PENDING','MATCHED','FAILED') DEFAULT 'PENDING',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipment_id) REFERENCES Shipments(shipment_id),
    FOREIGN KEY (requested_by) REFERENCES Users(user_id)
);

CREATE TABLE Match_Results (
    match_result_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_request_id  BIGINT NOT NULL,
    truck_id          BIGINT NOT NULL,
    availability_id   BIGINT NOT NULL,
    match_score       DECIMAL(5,2),
    status            ENUM('SUGGESTED','ACCEPTED','REJECTED') DEFAULT 'SUGGESTED',
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_request_id) REFERENCES Match_Requests(match_request_id),
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id),
    FOREIGN KEY (availability_id) REFERENCES Truck_Availability(availability_id)
);

CREATE TABLE Capacity_Reservations (
    reservation_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    availability_id   BIGINT NOT NULL,
    shipment_id       BIGINT NOT NULL,
    reserved_capacity_ton DECIMAL(6,2) NOT NULL,
    reserved_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    status            ENUM('HELD','CONFIRMED','RELEASED') DEFAULT 'HELD',
    FOREIGN KEY (availability_id) REFERENCES Truck_Availability(availability_id),
    FOREIGN KEY (shipment_id) REFERENCES Shipments(shipment_id)
);

-- ============================================================================
-- 8. ROUTE MANAGEMENT SERVICE
-- ============================================================================
CREATE TABLE Routes (
    route_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id           BIGINT NOT NULL,
    origin_city_id    INT NOT NULL,
    destination_city_id INT NOT NULL,
    distance_km       DECIMAL(8,2),
    estimated_duration_min INT,
    FOREIGN KEY (trip_id) REFERENCES Trips(trip_id),
    FOREIGN KEY (origin_city_id) REFERENCES Cities(city_id),
    FOREIGN KEY (destination_city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Route_Optimization (
    optimization_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id          BIGINT NOT NULL,
    optimized_path    JSON,              -- ordered list of lat/lng waypoints
    optimized_distance_km DECIMAL(8,2),
    optimized_duration_min INT,
    generated_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (route_id) REFERENCES Routes(route_id)
);

-- ============================================================================
-- 9. BOOKING SERVICE
-- ============================================================================
CREATE TABLE Bookings (
    booking_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_code      VARCHAR(30) NOT NULL UNIQUE,     -- e.g. BK-2024-124
    shipment_id       BIGINT NOT NULL,
    truck_id          BIGINT NOT NULL,
    availability_id   BIGINT NOT NULL,
    booked_by         BIGINT NOT NULL,
    pickup_date       DATE,
    status            ENUM('PENDING','ACCEPTED','DECLINED','IN_TRANSIT','COMPLETED','CANCELLED') DEFAULT 'PENDING',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipment_id) REFERENCES Shipments(shipment_id),
    FOREIGN KEY (truck_id) REFERENCES Trucks(truck_id),
    FOREIGN KEY (availability_id) REFERENCES Truck_Availability(availability_id),
    FOREIGN KEY (booked_by) REFERENCES Users(user_id)
);

CREATE TABLE Booking_Status_History (
    history_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id        BIGINT NOT NULL,
    status            VARCHAR(30) NOT NULL,
    changed_by        BIGINT,
    changed_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES Bookings(booking_id),
    FOREIGN KEY (changed_by) REFERENCES Users(user_id)
);

-- ============================================================================
-- 10. PRICING & PAYMENT SERVICE
-- ============================================================================
CREATE TABLE Pricing (
    pricing_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    origin_city_id     INT NOT NULL,
    destination_city_id INT NOT NULL,
    rate_per_ton       DECIMAL(10,2) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    FOREIGN KEY (origin_city_id) REFERENCES Cities(city_id),
    FOREIGN KEY (destination_city_id) REFERENCES Cities(city_id)
);

CREATE TABLE Payments (
    payment_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id        BIGINT NOT NULL,
    amount            DECIMAL(12,2) NOT NULL,
    payment_method    ENUM('CARD','BANK_TRANSFER','CASH') NOT NULL,
    status            ENUM('PENDING','COMPLETED','FAILED','REFUNDED') DEFAULT 'PENDING',
    paid_at           DATETIME,
    FOREIGN KEY (booking_id) REFERENCES Bookings(booking_id)
);

CREATE TABLE Transactions (
    transaction_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id         BIGINT NOT NULL,
    gateway_reference   VARCHAR(100),
    transaction_type    ENUM('DEBIT','CREDIT','REFUND') NOT NULL,
    amount               DECIMAL(12,2) NOT NULL,
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES Payments(payment_id)
);

CREATE TABLE Invoices (
    invoice_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id         BIGINT NOT NULL,
    invoice_no         VARCHAR(30) NOT NULL UNIQUE,
    total_amount        DECIMAL(12,2) NOT NULL,
    issued_date          DATE DEFAULT (CURRENT_DATE),
    status                ENUM('UNPAID','PAID','OVERDUE') DEFAULT 'UNPAID',
    FOREIGN KEY (booking_id) REFERENCES Bookings(booking_id)
);

-- ============================================================================
-- 11. NOTIFICATION SERVICE
-- ============================================================================
CREATE TABLE Notifications (
    notification_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    title              VARCHAR(150) NOT NULL,
    message            VARCHAR(500) NOT NULL,
    type               ENUM('BOOKING','PAYMENT','SYSTEM','GPS') DEFAULT 'SYSTEM',
    is_read            BOOLEAN DEFAULT FALSE,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

CREATE TABLE Notification_Logs (
    log_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id    BIGINT NOT NULL,
    channel            ENUM('EMAIL','PUSH','SMS') NOT NULL,
    status             ENUM('SENT','FAILED') NOT NULL,
    sent_at             DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (notification_id) REFERENCES Notifications(notification_id)
);

-- ============================================================================
-- 12. ANALYTICS & AUDIT SERVICE
-- ============================================================================
CREATE TABLE Activity_Logs (
    activity_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT,
    action              VARCHAR(150) NOT NULL,
    entity_type          VARCHAR(50),
    entity_id             BIGINT,
    created_at             DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);

CREATE TABLE Audit_Logs (
    audit_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name          VARCHAR(80) NOT NULL,
    record_id           BIGINT NOT NULL,
    operation            ENUM('INSERT','UPDATE','DELETE') NOT NULL,
    old_value             JSON,
    new_value             JSON,
    changed_by            BIGINT,
    changed_at             DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (changed_by) REFERENCES Users(user_id)
);

-- ============================================================================
-- 13. SYSTEM CONFIGURATION SERVICE
-- ============================================================================
CREATE TABLE System_Settings (
    setting_id          INT AUTO_INCREMENT PRIMARY KEY,
    setting_key          VARCHAR(100) NOT NULL UNIQUE,
    setting_value          VARCHAR(255),
    description              VARCHAR(255),
    updated_at                DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE API_Keys (
    api_key_id          INT AUTO_INCREMENT PRIMARY KEY,
    key_name             VARCHAR(100) NOT NULL,       -- e.g. GOOGLE_MAPS, FIREBASE_FCM
    key_value              VARCHAR(255) NOT NULL,
    environment              ENUM('DEV','STAGING','PROD') DEFAULT 'DEV',
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 14. DRIVER MOBILE APP
-- ============================================================================
CREATE TABLE Driver_Sessions (
    driver_session_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id            BIGINT NOT NULL,
    device_token          VARCHAR(255),         -- FCM push token
    app_version             VARCHAR(20),
    last_active_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES Drivers(driver_id)
);

SET FOREIGN_KEY_CHECKS = 1;
