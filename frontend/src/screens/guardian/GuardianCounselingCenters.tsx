import React from "react";
import { View, StyleSheet, Pressable, Linking } from "react-native";
import { useNavigation, useRoute } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { counseling } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import type { CounselingCenterResponse } from "@/api/types";
import { districtOptions } from "@/theme/districts";
import {
  brandColors,
  colors,
  guardian,
  facilityTypes,
  facilityTypeFallback,
  provinces,
  spacing,
  radius,
  fontSize,
  fontWeight,
} from "@/theme";
import {
  Screen,
  ScreenHeader,
  Card,
  Body,
  Caption,
  EmptyState,
  ErrorState,
  LoadingState,
  SentenceText as Text,
} from "@/components/ui";
import GuardianHeaderActions from "@/components/GuardianHeaderActions";

/**
 * 전문의 상담 예약 — `GET /counseling/centers`.
 *
 * The spec has no endpoint that lists administrative regions, so 시·도 comes
 * from the fixed code table in the theme and 시·군·구 is derived from the
 * centres the server returns for that province. The Figma prototype shipped a
 * hardcoded hospital directory; reproducing that would put institutions on
 * screen that the backend has never heard of.
 *
 * `reservation_mode` is `external_link` for the whole MVP (api-spec 12.1), so
 * the action here opens the institution's map entry rather than booking.
 */
function typeVisual(center: CounselingCenterResponse) {
  return facilityTypes[center.facility_type ?? ""] ?? facilityTypeFallback;
}

function Dropdown({
  label,
  placeholder,
  value,
  options,
  disabled,
  onSelect,
}: {
  label: string;
  placeholder: string;
  value: { code: string; name: string } | null;
  options: readonly { code: string; name: string }[];
  disabled?: boolean;
  onSelect: (option: { code: string; name: string }) => void;
}) {
  const [open, setOpen] = React.useState(false);

  // Collapse when the control becomes unusable, so a stale list cannot stay open.
  React.useEffect(() => {
    if (disabled) setOpen(false);
  }, [disabled]);

  return (
    <View>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Pressable
        onPress={() => setOpen((o) => !o)}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={`${label} 선택`}
        accessibilityState={{ expanded: open, disabled: !!disabled }}
        style={[
          styles.select,
          {
            backgroundColor: disabled ? colors.screenBackground : colors.muted,
            borderColor: value ? guardian.blue : colors.border,
            opacity: disabled ? 0.5 : 1,
          },
        ]}
      >
        <Text
          style={[
            styles.selectValue,
            { color: value ? colors.foreground : colors.mutedForeground },
          ]}
        >
          {value?.name ?? placeholder}
        </Text>
        <Ionicons
          name={open ? "chevron-up" : "chevron-down"}
          size={16}
          color={colors.mutedForeground}
        />
      </Pressable>

      {open ? (
        <View style={styles.optionList}>
          {options.map((option, i) => {
            const on = value?.code === option.code;
            return (
              <Pressable
                key={option.code}
                onPress={() => {
                  onSelect(option);
                  setOpen(false);
                }}
                accessibilityRole="button"
                accessibilityState={{ selected: on }}
                accessibilityLabel={option.name}
                style={[
                  styles.option,
                  {
                    // `colors.card` so the dropdown rows follow dark mode.
                    backgroundColor: on ? guardian.blueLight : colors.card,
                    borderTopWidth: i > 0 ? 1 : 0,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.optionLabel,
                    {
                      color: on ? guardian.blue : colors.foreground,
                      fontWeight: on ? fontWeight.bold : fontWeight.normal,
                    },
                  ]}
                >
                  {option.name}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}


/** 카카오 결과의 링크는 기관 홈페이지가 아니라 카카오 장소 상세라서 라벨을 구분한다. */
function siteLabel(center: CounselingCenterResponse) {
  return center.source_name === "카카오 로컬" ? "카카오 장소 정보" : "기관 사이트";
}

/** 등록 기관과 카카오 검색 결과가 같은 모양으로 보이도록 카드를 하나로 둔다. */
function CenterCard({
  center,
  onKakaoMap,
  onNaverMap,
}: {
  center: CounselingCenterResponse;
  onKakaoMap: (center: CounselingCenterResponse) => void;
  onNaverMap: (center: CounselingCenterResponse) => void;
}) {
  const visual = typeVisual(center);
  return (
    <Card>
      <Body style={{ fontWeight: fontWeight.bold }}>{center.name}</Body>
      <View
        style={[styles.typeBadge, { backgroundColor: visual.background }]}
      >
        <Text style={[styles.typeLabel, { color: visual.color }]}>
          {visual.label}
        </Text>
      </View>

      {center.address ? (
        <View style={styles.addressRow}>
          <Ionicons
            name="location-outline"
            size={14}
            color={colors.mutedForeground}
          />
          <Caption style={{ flex: 1 }}>{center.address}</Caption>
        </View>
      ) : null}

      <View style={styles.actions}>
        <View style={styles.linkGroup}>
          {center.phone ? (
            <Pressable
              onPress={() => void Linking.openURL(`tel:${center.phone}`)}
              accessibilityRole="button"
              accessibilityLabel={`${center.name} 전화 걸기`}
              style={styles.linkButton}
            >
              <Ionicons name="call-outline" size={14} color={guardian.blue} />
              <Text style={styles.linkLabel}>{center.phone}</Text>
            </Pressable>
          ) : null}
          {center.homepage_url ? (
            <Pressable
              onPress={() => void Linking.openURL(center.homepage_url as string)}
              accessibilityRole="link"
              accessibilityLabel={`${center.name} ${siteLabel(center)} 열기`}
              style={styles.linkButton}
            >
              <Ionicons name="globe-outline" size={14} color={guardian.blue} />
              <Text style={styles.linkLabel}>{siteLabel(center)}</Text>
            </Pressable>
          ) : null}
        </View>
        <View style={styles.mapGroup}>
          <Pressable
            onPress={() => onKakaoMap(center)}
            accessibilityRole="button"
            accessibilityLabel={`${center.name} 카카오맵에서 보기`}
            style={[styles.mapButton, styles.kakaoButton]}
          >
            <Ionicons name="map-outline" size={14} color={KAKAO_LABEL} />
            <Text style={[styles.mapLabel, { color: KAKAO_LABEL }]}>카카오맵</Text>
          </Pressable>
          <Pressable
            onPress={() => onNaverMap(center)}
            accessibilityRole="button"
            accessibilityLabel={`${center.name} 네이버 지도에서 보기`}
            style={styles.mapButton}
          >
            <Ionicons name="map-outline" size={14} color={colors.white} />
            <Text style={styles.mapLabel}>네이버 지도</Text>
          </Pressable>
        </View>
      </View>
    </Card>
  );
}

/** `facility_type` filter chips — 치매안심센터를 먼저 둔다 (무료 검사·상담 진입점). */
const TYPE_FILTERS = [
  { key: null, label: "전체" },
  { key: "dementia_center", label: "치매안심센터" },
  { key: "public_health_center", label: "보건소" },
  { key: "hospital", label: "치매 진료 병원" },
] as const;

export default function GuardianCounselingCentersScreen() {
  const navigation = useNavigation();
  const route = useRoute();
  // This component is both a bottom tab and a pushed dashboard detail. Tab
  // history also makes canGoBack() true, so the route name is the reliable way
  // to avoid adding a back row (and changing header height) on the main tab.
  const isDashboardDetail = route.name === "GuardianCounselingCenters";
  const [province, setProvince] = React.useState<{ code: string; name: string } | null>(null);
  const [district, setDistrict] = React.useState<{ code: string; name: string } | null>(null);
  const [facilityType, setFacilityType] = React.useState<string | null>(null);

  // The province query is deliberately unfiltered: the 시·군·구 options are
  // derived from the response, and filtering server-side would hide districts
  // that only have the other facility types.
  const centers = useApi(
    () => counseling.centers(province?.code as string),
    [province?.code],
    { enabled: !!province },
  );

  // 시·군·구 is a fixed table so it can always be chosen, even where the server
  // holds no registered centres; the name is what both the server filter and the
  // Kakao search use (#138).
  const districts = React.useMemo(
    () => (province ? districtOptions(province.code) : []),
    [province],
  );

  const visible = (centers.data?.centers ?? [])
    .filter((c) => !district || c.district_name === district.name)
    .filter((c) => !facilityType || c.facility_type === facilityType);

  // Kakao Local results for the same region, fetched by the server so the REST
  // key never ships in the app. Registered centres render first, these follow.
  const nearby = useApi(
    () => counseling.nearby(province?.name as string, district?.name, facilityType ?? undefined),
    [province?.name, district?.name, facilityType],
    { enabled: !!province },
  );
  // Kakao keyword search also returns neighbouring districts; list the ones whose
  // address actually sits in the selected 시·군·구 first so "강남구" reads as 강남구.
  const inDistrict = (c: CounselingCenterResponse) =>
    !district || (c.address ?? "").includes(district.name);
  const nearbyCenters = (nearby.data?.centers ?? [])
    .filter((c) => !visible.some((v) => v.name === c.name && v.address === c.address))
    .sort((a, b) => Number(inDistrict(b)) - Number(inDistrict(a)));
  const providerOk = nearby.data?.provider_status === "ok";

  const openMap = (center: CounselingCenterResponse) => {
    const url =
      center.naver_map_url ??
      `https://map.naver.com/p/search/${encodeURIComponent(center.name)}`;
    void Linking.openURL(url);
  };

  // 카카오맵 URL 스킴 — 좌표가 있으면 핀 링크, 없으면 이름 검색 링크 (#136).
  // 서버 응답의 latitude/longitude를 그대로 쓰므로 백엔드 변경은 없다.
  const openKakaoMap = (center: CounselingCenterResponse) => {
    const name = encodeURIComponent(center.name);
    const url =
      center.latitude !== null && center.longitude !== null
        ? `https://map.kakao.com/link/map/${name},${center.latitude},${center.longitude}`
        : `https://map.kakao.com/link/search/${name}`;
    void Linking.openURL(url);
  };

  // 선택한 시/도(+시/군/구)와 시설 키워드로 카카오맵 검색을 연다. 서버에 등록된 기관이
  // 없는 지역도 지도에서 바로 찾을 수 있게 하는 경로라 기관 데이터에 의존하지 않는다 (#136).
  const openKakaoSearch = (keyword: string) => {
    const region = [province?.name, district?.name].filter(Boolean).join(" ");
    const query = encodeURIComponent(`${region} ${keyword}`.trim());
    void Linking.openURL(`https://map.kakao.com/link/search/${query}`);
  };

  return (
    <Screen
      header={
        <ScreenHeader
          color={guardian.blue}
          onBack={isDashboardDetail ? () => navigation.goBack() : undefined}
          backLabel={isDashboardDetail ? "대시보드" : undefined}
          title="전문의 상담 예약"
          subtitle="지역을 선택하면 치매안심센터·병원·보건소를 안내해 드려요."
          right={<GuardianHeaderActions />}
        />
      }
    >
      <View style={{ gap: spacing.md }}>
        <Dropdown
          label="시 / 도"
          placeholder="시/도를 선택하세요"
          value={province}
          options={provinces}
          onSelect={(option) => {
            setProvince(option);
            setDistrict(null);
          }}
        />
        <Dropdown
          label="시 / 군 / 구"
          placeholder={
            !province
              ? "시/도를 먼저 선택하세요"
              : districts.length === 0
                ? "등록된 기관이 없어요"
                : "시/군/구를 선택하세요"
          }
          value={district}
          options={districts}
          disabled={!province || districts.length === 0}
          onSelect={setDistrict}
        />
      </View>

      {province ? (
        <View style={styles.typeFilterRow}>
          {TYPE_FILTERS.map((filter) => {
            const on = facilityType === filter.key;
            return (
              <Pressable
                key={filter.label}
                onPress={() => setFacilityType(filter.key)}
                accessibilityRole="tab"
                accessibilityState={{ selected: on }}
                accessibilityLabel={`${filter.label} 기관만 보기`}
                style={[
                  styles.typeFilter,
                  {
                    // `colors.card` so unselected filters stay legible in dark.
                    backgroundColor: on ? guardian.blue : colors.card,
                    borderColor: on ? guardian.blue : colors.border,
                  },
                ]}
              >
                <Text
                  style={[
                    styles.typeFilterLabel,
                    { color: on ? colors.white : colors.mutedForeground },
                  ]}
                >
                  {filter.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}

      {province ? (
        <Caption style={{ marginTop: spacing.md }}>
          가까운 치매안심센터에서 무료 검사·상담을 받으실 수 있어요.
        </Caption>
      ) : null}

      {province && nearby.data && !providerOk ? (
        <View style={styles.kakaoSearchCard}>
          <Caption>
            {province.name} {district?.name ?? ""} 주변을 카카오맵에서 바로 찾기
          </Caption>
          <View style={styles.kakaoSearchRow}>
            {KAKAO_SEARCH_KEYWORDS.map((item) => (
              <Pressable
                key={item.keyword}
                onPress={() => openKakaoSearch(item.keyword)}
                accessibilityRole="link"
                accessibilityLabel={`카카오맵에서 ${province.name} ${district?.name ?? ""} ${item.label} 찾기`}
                style={[styles.mapButton, styles.kakaoButton]}
              >
                <Ionicons name="search-outline" size={14} color={KAKAO_LABEL} />
                <Text style={[styles.mapLabel, { color: KAKAO_LABEL }]}>{item.label} 찾기</Text>
              </Pressable>
            ))}
          </View>
        </View>
      ) : null}

      <View style={styles.results}>
        {!province ? (
          <EmptyState
            message={"지역을 선택해 주세요.\n시/도를 고르면 해당 지역의 기관이 표시돼요."}
            icon="location-outline"
          />
        ) : centers.error ? (
          <ErrorState message={apiErrorMessage(centers.error)} onRetry={centers.reload} />
        ) : centers.loading && !centers.data ? (
          <LoadingState label="기관을 불러오는 중이에요" />
        ) : visible.length === 0 && nearbyCenters.length === 0 ? (
          nearby.loading ? (
            <LoadingState label="주변 기관을 찾고 있어요" />
          ) : (
            <EmptyState
              message={
                providerOk
                  ? "이 지역에서 찾은 기관이 없어요.\n시/군/구나 유형을 바꿔 보세요."
                  : "이 지역에 등록된 기관이 없어요.\n위의 카카오맵 검색으로 주변 기관을 찾아보세요."
              }
              icon="location-outline"
            />
          )
        ) : (
          <>
            {visible.length > 0 ? (
              <>
                <Caption style={{ marginBottom: spacing.md }}>
                  {province.name} {district?.name ?? ""} · 등록 기관 {visible.length}개
                </Caption>
                <View style={{ gap: spacing.md }}>
                  {visible.map((center) => (
                    <CenterCard
                      key={center.center_id}
                      center={center}
                      onKakaoMap={openKakaoMap}
                      onNaverMap={openMap}
                    />
                  ))}
                </View>
              </>
            ) : null}
            {nearby.loading && !nearby.data ? (
              <LoadingState label="주변 기관을 찾고 있어요" />
            ) : nearbyCenters.length > 0 ? (
              <>
                <Caption style={{ marginTop: visible.length > 0 ? spacing.lg : 0, marginBottom: spacing.md }}>
                  카카오맵 검색 결과 · {nearbyCenters.length}개
                </Caption>
                <View style={{ gap: spacing.md }}>
                  {nearbyCenters.map((center) => (
                    <CenterCard
                      key={center.center_id}
                      center={center}
                      onKakaoMap={openKakaoMap}
                      onNaverMap={openMap}
                    />
                  ))}
                </View>
              </>
            ) : null}
          </>
        )}
      </View>
    </Screen>
  );
}

/** 카카오 공식 버튼 색 — 배경 #FEE500, 라벨 #191919 (카카오 디자인 가이드). */
const KAKAO_YELLOW = "#FEE500";
const KAKAO_LABEL = "#191919";

/** 카카오맵 검색 키워드 — 화면의 시설 유형 칩과 같은 순서. */
const KAKAO_SEARCH_KEYWORDS: { label: string; keyword: string }[] = [
  { label: "치매안심센터", keyword: "치매안심센터" },
  { label: "보건소", keyword: "보건소" },
  { label: "치매 진료 병원", keyword: "치매 진료 병원" },
];

const styles = StyleSheet.create({
  fieldLabel: {
    fontSize: fontSize.micro,
    fontWeight: fontWeight.semibold,
    color: colors.mutedForeground,
    marginBottom: 6,
  },
  select: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    height: 46,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1,
  },
  selectValue: { fontSize: fontSize.bodyLg },
  optionList: {
    marginTop: spacing.xs,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: "hidden",
  },
  option: {
    height: 44,
    justifyContent: "center",
    paddingHorizontal: spacing.lg,
    borderTopColor: colors.border,
  },
  optionLabel: { fontSize: fontSize.bodyLg },

  typeFilterRow: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm, marginTop: spacing.lg },
  typeFilter: {
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  typeFilterLabel: { fontSize: fontSize.micro, fontWeight: fontWeight.semibold },

  results: { marginTop: spacing.xl },
  typeBadge: {
    alignSelf: "flex-start",
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    marginTop: spacing.xs,
  },
  typeLabel: { fontSize: fontSize.badge, fontWeight: fontWeight.bold },
  addressRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: spacing.md,
  },
  actions: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: spacing.md,
    marginTop: spacing.md,
  },
  linkGroup: { flex: 1, gap: 2 },
  linkButton: { flexDirection: "row", alignItems: "center", gap: 5, minHeight: 44 },
  linkLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: guardian.blue },
  mapButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    height: 44,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    backgroundColor: brandColors.naverGreen,
  },
  mapLabel: { fontSize: fontSize.caption, fontWeight: fontWeight.semibold, color: colors.white },
  mapGroup: { gap: spacing.sm },
  kakaoButton: { backgroundColor: KAKAO_YELLOW },
  kakaoSearchCard: { marginTop: spacing.md, gap: spacing.sm },
  kakaoSearchRow: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
});
