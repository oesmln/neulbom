CREATE TABLE counseling_centers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    facility_type VARCHAR(50) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    district_code VARCHAR(20),
    province_name VARCHAR(100) NOT NULL,
    district_name VARCHAR(100),
    address VARCHAR(500) NOT NULL,
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    phone VARCHAR(50),
    map_url VARCHAR(1000),
    website_url VARCHAR(1000),
    reservation_mode VARCHAR(30) NOT NULL DEFAULT 'external_link',
    source_name VARCHAR(200) NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_counseling_centers_reservation_mode CHECK (reservation_mode IN ('external_link', 'internal'))
);

CREATE INDEX idx_counseling_centers_region
    ON counseling_centers (province_code, district_code, facility_type, active);

INSERT INTO counseling_centers (
    id, name, facility_type, province_code, district_code, province_name, district_name,
    address, latitude, longitude, phone, map_url, website_url, reservation_mode, source_name
) VALUES
    ('00000000-0000-0000-0000-000000009001', '부산대학교병원 신경과', 'hospital', '26', '26350', '부산광역시', '해운대구',
     '부산광역시 해운대구 APEC로 170', 35.1712, 129.1284, '051-240-7000',
     'https://map.naver.com/p/search/부산대학교병원', 'https://www.pnuh.or.kr', 'external_link', '공공기관 기준정보'),
    ('00000000-0000-0000-0000-000000009002', '해운대구 치매안심센터', 'dementia_center', '26', '26350', '부산광역시', '해운대구',
     '부산광역시 해운대구 반여로 30', 35.2074, 129.1262, '051-749-7575',
     'https://map.naver.com/p/search/해운대구%20치매안심센터', 'https://www.haeundae.go.kr', 'external_link', '공공기관 기준정보'),
    ('00000000-0000-0000-0000-000000009003', '해운대구 보건소', 'public_health_center', '26', '26350', '부산광역시', '해운대구',
     '부산광역시 해운대구 양운로 100', 35.1638, 129.1631, '051-746-4000',
     'https://map.naver.com/p/search/해운대구%20보건소', 'https://www.haeundae.go.kr/health', 'external_link', '공공기관 기준정보');
