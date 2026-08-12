import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import {
  createBottomTabNavigator,
  type BottomTabBarProps,
} from "@react-navigation/bottom-tabs";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { ElderStackParamList, ElderTabParamList } from "@/navigation/types";
import { colors, fontSize, fontWeight, sizes, shadow } from "@/theme";

import ElderHomeScreen from "@/screens/elder/ElderHome";
import ElderCistScreen from "@/screens/elder/ElderCist";
import ElderAiChatScreen from "@/screens/elder/ElderAiChat";
import ElderResultScreen from "@/screens/elder/ElderResult";
import ElderNotificationsScreen from "@/screens/elder/ElderNotifications";
import ElderCalendarScreen from "@/screens/elder/ElderCalendar";
import ElderCampaignScreen from "@/screens/elder/ElderCampaign";
import ElderGameHubScreen from "@/screens/elder/ElderGameHub";
import ElderMyPageScreen from "@/screens/elder/ElderMyPage";

const Tab = createBottomTabNavigator<ElderTabParamList>();
const Stack = createNativeStackNavigator<ElderStackParamList>();

type TabKey = keyof ElderTabParamList;

const TAB_META: Record<TabKey, { label: string; icon: keyof typeof Ionicons.glyphMap }> = {
  ElderAiChat: { label: "AI 대화", icon: "chatbubbles-outline" },
  ElderCalendar: { label: "일기", icon: "calendar-outline" },
  ElderHome: { label: "홈", icon: "home" },
  ElderGameHub: { label: "게임", icon: "game-controller-outline" },
  ElderMyPage: { label: "마이", icon: "person-outline" },
};

/**
 * Bottom bar with 홈 raised out of the middle.
 *
 * The default tab bar cannot lift one item above the bar, so this replaces it
 * wholesale. The raised button overflows the bar upwards, which means the bar
 * itself must not clip — hence no `overflow: hidden` anywhere on the way down.
 */
function ElderTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.tabBar, { height: sizes.tabBarHeight + insets.bottom, paddingBottom: insets.bottom + 8 }]}>
      {state.routes.map((route, index) => {
        const key = route.name as TabKey;
        const meta = TAB_META[key];
        const focused = state.index === index;
        const isHome = key === "ElderHome";

        const onPress = () => {
          const event = navigation.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
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
            {isHome ? (
              <View
                style={[
                  styles.homeButton,
                  {
                    backgroundColor: focused ? colors.primary : colors.white,
                    borderColor: focused ? colors.primary : colors.border,
                  },
                ]}
              >
                <Ionicons
                  name="home"
                  size={22}
                  color={focused ? colors.white : colors.mutedForeground}
                />
              </View>
            ) : (
              <Ionicons
                name={meta.icon}
                size={21}
                color={focused ? colors.primary : colors.mutedForeground}
              />
            )}
            <Text
              style={[
                styles.tabLabel,
                {
                  color: focused ? colors.primary : colors.mutedForeground,
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

function ElderTabs() {
  return (
    <Tab.Navigator
      initialRouteName="ElderHome"
      screenOptions={{ headerShown: false }}
      tabBar={(props) => <ElderTabBar {...props} />}
    >
      <Tab.Screen name="ElderAiChat" component={ElderAiChatScreen} />
      <Tab.Screen name="ElderCalendar" component={ElderCalendarScreen} />
      <Tab.Screen name="ElderHome" component={ElderHomeScreen} />
      <Tab.Screen name="ElderGameHub" component={ElderGameHubScreen} />
      <Tab.Screen name="ElderMyPage" component={ElderMyPageScreen} />
    </Tab.Navigator>
  );
}

export default function ElderNavigator() {
  return (
    // The initial CIST screening comes before the tabs, matching the Figma
    // flow: 유형 선택 → CIST 초기 검사 → 홈. Finishing it replaces this route,
    // so 뒤로 가기 cannot land back in the middle of a completed screening.
    <Stack.Navigator initialRouteName="ElderCist" screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ElderCist" component={ElderCistScreen} />
      <Stack.Screen name="ElderTabs" component={ElderTabs} />
      <Stack.Screen name="ElderResult" component={ElderResultScreen} />
      <Stack.Screen name="ElderNotifications" component={ElderNotificationsScreen} />
      <Stack.Screen name="ElderCampaign" component={ElderCampaignScreen} />
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
  tabLabel: { fontSize: 10 },
  homeButton: {
    position: "absolute",
    bottom: 20,
    width: sizes.tabBarHomeButton,
    height: sizes.tabBarHomeButton,
    borderRadius: sizes.tabBarHomeButton / 2,
    borderWidth: 2,
    alignItems: "center",
    justifyContent: "center",
    ...shadow.floating,
  },
});
