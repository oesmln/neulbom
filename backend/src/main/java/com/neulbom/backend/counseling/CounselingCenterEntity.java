package com.neulbom.backend.counseling;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "counseling_centers")
public class CounselingCenterEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "province_code", nullable = false, length = 20)
    private String provinceCode;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Column(name = "district_name", length = 100)
    private String districtName;

    @Column(nullable = false, length = 500)
    private String address;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;

    @Column(name = "map_url", length = 1000)
    private String mapUrl;

    @Column(name = "website_url", length = 1000)
    private String websiteUrl;

    @Column(name = "reservation_mode", nullable = false, length = 30)
    private String reservationMode;

    @Column(name = "source_name", nullable = false, length = 200)
    private String sourceName;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CounselingCenterEntity() {
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getFacilityType() { return facilityType; }
    public String getProvinceCode() { return provinceCode; }
    public String getDistrictCode() { return districtCode; }
    public String getProvinceName() { return provinceName; }
    public String getDistrictName() { return districtName; }
    public String getAddress() { return address; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getPhone() { return phone; }
    public String getMapUrl() { return mapUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getReservationMode() { return reservationMode; }
    public String getSourceName() { return sourceName; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
    public boolean isActive() { return active; }
}
