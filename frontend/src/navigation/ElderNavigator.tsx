import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import {
  createBottomTabNavigator,
  type BottomTabBarProps,
} from "@react-navigation/bottom-tabs";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import {
  ElderMyPageStackParamList,
  ElderStackParamList,
  ElderTabParamList,
} from "@/navigation/types";
import { colors, fontWeight, sizes } from "@/theme";

import ElderHomeScreen from "@/screens/elder/ElderHome";
import ElderCistScreen from "@/screens/elder/ElderCist";
import ElderAiChatScreen from "@/screens/elder/ElderAiChat";
import ElderResultScreen from "@/screens/elder/ElderResult";
import ElderNotificationsScreen from "@/screens/elder/ElderNotifications";
import ElderCalendarScreen from "@/screens/elder/ElderCalendar";
import ElderCampaignScreen from "@/screens/elder/ElderCampaign";
import ElderGameHubScreen from "@/screens/elder/ElderGameHub";
import ElderMyPageScreen from "@/screens/elder/ElderMyPage";
import ElderPasswordChangeScreen from "@/screens/elder/ElderPasswordChange";
import ElderAppSettingsScreen from "@/screens/elder/ElderAppSettings";
import ElderGameCardMatchScreen from "@/screens/elder/games/ElderGameCardMatch";
import ElderGameColorScreen from "@/screens/elder/games/ElderGameColor";
import ElderGameConsonantScreen from "@/screens/elder/games/ElderGameConsonant";

const Tab = createBottomTabNavigator<ElderTabParamList>();
const Stack = createNativeStackNavigator<ElderStackParamList>();
const MyPageStack = createNativeStackNavigator<ElderMyPageStackParamList>();

/**
 * 마이 탭 안의 스택. 앱 설정·비밀번호 변경을 탭 위로 push하면 하단 탭바가
 * 사라지므로, 마이 탭 내부에서 열어 탭바를 유지한다.
 */
function ElderMyPageStackNavigator() {
  return (
    <MyPageStack.Navigator initialRouteName="ElderMyPageMain" screenOptions={{ headerShown: false }}>
      <MyPageStack.Screen name="ElderMyPageMain" component={ElderMyPageScreen} />
      <MyPageStack.Screen name="ElderPasswordChange" component={ElderPasswordChangeScreen} />
      <MyPageStack.Screen name="ElderAppSettings" component={ElderAppSettingsScreen} />
    </MyPageStack.Navigator>
  );
}

type TabKey = keyof ElderTabParamList;

const TAB_META: Record<TabKey, { label: string; icon: keyof typeof Ionicons.glyphMap }> = {
  ElderAiChat: { label: "AI 대화", icon: "chatbubbles-outline" },
  ElderCalendar: { label: "일기", icon: "calendar-outline" },
  ElderHome: { label: "홈", icon: "home-outline" },
  ElderGameHub: { label: "게임", icon: "game-controller-outline" },
  ElderMyPage: { label: "마이", icon: "person-outline" },
};

/**
 * Bottom bar — five tabs of the same shape.
 *
 * The Figma lifts 홈 out of the bar as a raised circle; the team asked for a
 * flat bar instead, so every tab is icon + label and only colour and weight
 * mark the active one. Still a custom bar rather than the default one because
 * the default cannot change icon stroke weight between states.
 */
function ElderTabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.tabBar, { height: sizes.tabBarHeight + insets.bottom, paddingBottom: insets.bottom + 8 }]}>
      {state.routes.map((route, index) => {
        const key = route.name as TabKey;
        const meta = TAB_META[key];
        const focused = state.index === index;

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
            <Ionicons
              name={meta.icon}
              size={21}
              color={focused ? colors.primary : colors.mutedForeground}
            />
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
      <Tab.Screen name="ElderMyPage" component={ElderMyPageStackNavigator} />
    </Tab.Navigator>
  );
}

export default function ElderNavigator() {
  return (
    // Entering the elder area means an authenticated login and must always
    // land on home. New sign-ups enter CIST explicitly from Onboarding.
    <Stack.Navigator initialRouteName="ElderTabs" screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ElderCist" component={ElderCistScreen} />
      <Stack.Screen name="ElderTabs" component={ElderTabs} />
      <Stack.Screen name="ElderResult" component={ElderResultScreen} />
      <Stack.Screen name="ElderNotifications" component={ElderNotificationsScreen} />
      <Stack.Screen name="ElderCampaign" component={ElderCampaignScreen} />
      <Stack.Screen name="ElderGameCardMatch" component={ElderGameCardMatchScreen} />
      <Stack.Screen name="ElderGameColor" component={ElderGameColorScreen} />
      <Stack.Screen name="ElderGameConsonant" component={ElderGameConsonantScreen} />
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
});
