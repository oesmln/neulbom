import React from "react";
import { View, StyleSheet, Pressable, Linking } from "react-native";
import { useNavigation, useRoute } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";

import { counseling } from "@/api";
import { useApi } from "@/hooks/useApi";
import { apiErrorMessage } from "@/api/errors";
import type { CounselingCenterResponse } from "@/api/types";
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

/** `facility_type` filter chips, in the order api-spec 12.1 lists the enum. */
const TYPE_FILTERS = [
  { key: null, label: "전체" },
  { key: "hospital", label: "병원" },
  { key: "dementia_center", label: "치매안심센터" },
  { key: "public_health_center", label: "보건소" },
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

  // 시·군·구 options are whatever the province's centres actually sit in.
  const districts = React.useMemo(() => {
    const seen = new Map<string, string>();
    for (const c of centers.data?.centers ?? []) {
      if (c.district_code && c.district_name) seen.set(c.district_code, c.district_name);
    }
    return [...seen].map(([code, name]) => ({ code, name }));
  }, [centers.data]);

  const visible = (centers.data?.centers ?? [])
    .filter((c) => !district || c.district_code === district.code)
    .filter((c) => !facilityType || c.facility_type === facilityType);

  const openMap = (center: CounselingCenterResponse) => {
    const url =
      center.naver_map_url ??
      `https://map.naver.com/p/search/${encodeURIComponent(center.name)}`;
    void Linking.openURL(url);
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
        ) : visible.length === 0 ? (
          <EmptyState message="이 지역에 등록된 기관이 없어요." icon="location-outline" />
        ) : (
          <>
            <Caption style={{ marginBottom: spacing.md }}>
              {province.name} {district?.name ?? ""} · {visible.length}개 기관
            </Caption>
            <View style={{ gap: spacing.md }}>
              {visible.map((center) => {
                const visual = typeVisual(center);
                return (
                  <Card key={center.center_id}>
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
                            accessibilityLabel={`${center.name} 기관 사이트 열기`}
                            style={styles.linkButton}
                          >
                            <Ionicons name="globe-outline" size={14} color={guardian.blue} />
                            <Text style={styles.linkLabel}>기관 사이트</Text>
                          </Pressable>
                        ) : null}
                      </View>
                      <Pressable
                        onPress={() => openMap(center)}
                        accessibilityRole="button"
                        accessibilityLabel={`${center.name} 네이버 지도에서 보기`}
                        style={styles.mapButton}
                      >
                        <Ionicons name="map-outline" size={14} color={colors.white} />
                        <Text style={styles.mapLabel}>네이버 지도</Text>
                      </Pressable>
                    </View>
                  </Card>
                );
              })}
            </View>
          </>
        )}
      </View>
    </Screen>
  );
}

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
});
