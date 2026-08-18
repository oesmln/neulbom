import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import {
  createBottomTabNavigator,
  type BottomTabBarProps,
} from "@react-navigation/bottom-tabs";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { GuardianStackParamList, GuardianTabParamList } from "@/navigation/types";
import { colors, fontSize, fontWeight, guardian, sizes } from "@/theme";
import { useDisplaySettings } from "@/store/DisplaySettingsContext";

import GuardianDashboardScreen from "@/screens/guardian/GuardianDashboard";
import GuardianDiaryScreen from "@/screens/guardian/GuardianDiary";
import GuardianChartScreen from "@/screens/guardian/GuardianChart";
import GuardianNotificationsScreen from "@/screens/guardian/GuardianNotifications";
import GuardianCounselingCentersScreen from "@/screens/guardian/GuardianCounselingCenters";
import GuardianAppSettingsScreen from "@/screens/guardian/GuardianAppSettings";
import GuardianSettingsScreen from "@/screens/guardian/GuardianSettings";
import GuardianConnectionsScreen from "@/screens/guardian/GuardianConnections";
import ElderPasswordChangeScreen from "@/screens/elder/ElderPasswordChange";

const Tab = createBottomTabNavigator<GuardianTabParamList>();
const Stack = createNativeStackNavigator<GuardianStackParamList>();

type TabKey = keyof GuardianTabParamList;

const TAB_META: Record<TabKey, { label: string; icon: keyof typeof Ionicons.glyphMap }> = {
  GuardianDashboard: { label: "홈", icon: "home-outline" },
  GuardianRecord: { label: "기록", icon: "document-text-outline" },
  GuardianChart: { label: "추이", icon: "bar-chart-outline" },
  GuardianAppointments: { label: "예약", icon: "calendar-outline" },
  GuardianSettings: { label: "설정", icon: "settings-outline" },
};

/**
 * Guardian tab bar.
 *
 * Hand-drawn rather than configured through `screenOptions` because the design
 * changes icon stroke weight as well as colour between states, and the default
 * bar only exposes a tint colour.
 */
function GuardianTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  useDisplaySettings();

  return (
    <View
      style={[
        styles.tabBar,
        { height: sizes.tabBarHeight + insets.bottom, paddingBottom: insets.bottom + 8 },
      ]}
    >
      {state.routes.map((route, index) => {
        const key = route.name as TabKey;
        const meta = TAB_META[key];
        const focused = state.index === index;

        const onPress = () => {
          const event = navigation.emit({
            type: "tabPress",
            target: route.key,
            canPreventDefault: true,
          });
          if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
        };

        return (
          <Pressable
            key={route.key}
            onPress={onPress}
            accessibilityRole="tab"
            accessibilityState={{ selected: focused }}
            accessibilityLabel={meta.label}
            style={styles.tab}
          >
            <Ionicons
              name={meta.icon}
              size={20}
              color={focused ? guardian.blue : colors.mutedForeground}
            />
            <Text
              style={[
                styles.tabLabel,
                {
                  color: focused ? guardian.blue : colors.mutedForeground,
                  fontWeight: focused ? fontWeight.semibold : fontWeight.normal,
                },
              ]}
            >
              {meta.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function GuardianTabs() {
  return (
    <Tab.Navigator
      initialRouteName="GuardianDashboard"
      screenOptions={{ headerShown: false }}
      tabBar={(props) => <GuardianTabBar {...props} />}
    >
      <Tab.Screen name="GuardianDashboard" component={GuardianDashboardScreen} />
      <Tab.Screen name="GuardianRecord" component={GuardianDiaryScreen} />
      <Tab.Screen name="GuardianChart" component={GuardianChartScreen} />
      <Tab.Screen name="GuardianAppointments" component={GuardianCounselingCentersScreen} />
      <Tab.Screen name="GuardianSettings" component={GuardianSettingsScreen} />
    </Tab.Navigator>
  );
}

function GuardianPasswordChangeScreen() {
  return <ElderPasswordChangeScreen backLabel="설정" />;
}

export default function GuardianNavigator() {
  return (
    <Stack.Navigator initialRouteName="GuardianTabs" screenOptions={{ headerShown: false }}>
      <Stack.Screen name="GuardianTabs" component={GuardianTabs} />
      <Stack.Screen name="GuardianNotifications" component={GuardianNotificationsScreen} />
      <Stack.Screen
        name="GuardianCounselingCenters"
        component={GuardianCounselingCentersScreen}
      />
      <Stack.Screen name="GuardianAppSettings" component={GuardianAppSettingsScreen} />
      <Stack.Screen name="GuardianPasswordChange" component={GuardianPasswordChangeScreen} />
      <Stack.Screen name="GuardianConnections" component={GuardianConnectionsScreen} />
    </Stack.Navigator>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: "row",
    alignItems: "flex-end",
    backgroundColor: colors.white,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  tab: { flex: 1, alignItems: "center", justifyContent: "flex-end", gap: 4, paddingBottom: 6 },
  tabLabel: { fontSize: fontSize.badge },
});
