import React from "react";
import { LogBox } from "react-native";
import { StatusBar } from "expo-status-bar";
import { NavigationContainer, DefaultTheme } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { RootStackParamList } from "@/navigation/types";
import { navigationRef } from "@/navigation/ref";
import { colors, isDarkApplied } from "@/theme";
import { AppProvider } from "@/store/AppContext";

import SplashScreen from "@/screens/auth/SplashScreen";
import LoginScreen from "@/screens/auth/LoginScreen";
import UserTypeScreen from "@/screens/auth/UserTypeScreen";
import EmailVerificationScreen from "@/screens/auth/EmailVerificationScreen";
import SignupCompleteScreen from "@/screens/auth/SignupCompleteScreen";
import ElderProfileScreen from "@/screens/auth/ElderProfileScreen";
import OnboardingScreen from "@/screens/auth/OnboardingScreen";
import ElderNavigator from "@/navigation/ElderNavigator";
import GuardianNavigator from "@/navigation/GuardianNavigator";

// three still constructs a THREE.Clock internally and warns about it on every
// mount. The toast it raises sits over the bottom tab bar, which makes the tab
// bar untappable in dev — nothing we can fix upstream, so mute the one line.
LogBox.ignoreLogs(["THREE.Clock: This module has been deprecated"]);

const Stack = createNativeStackNavigator<RootStackParamList>();

const navTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: colors.background,
    primary: colors.primary,
    card: colors.background,
    text: colors.foreground,
    border: colors.border,
  },
};

export default function App() {
  return (
    <SafeAreaProvider>
      <AppProvider>
        <NavigationContainer ref={navigationRef} theme={navTheme}>
          <StatusBar style={isDarkApplied() ? "light" : "dark"} />
          <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Splash" component={SplashScreen} />
            <Stack.Screen name="Login" component={LoginScreen} />
            <Stack.Screen name="UserType" component={UserTypeScreen} />
            <Stack.Screen name="EmailVerification" component={EmailVerificationScreen} />
            <Stack.Screen name="SignupComplete" component={SignupCompleteScreen} />
            <Stack.Screen name="ElderProfile" component={ElderProfileScreen} />
            <Stack.Screen name="Onboarding" component={OnboardingScreen} />
            <Stack.Screen name="Elder" component={ElderNavigator} />
            <Stack.Screen name="Guardian" component={GuardianNavigator} />
          </Stack.Navigator>
        </NavigationContainer>
      </AppProvider>
    </SafeAreaProvider>
  );
}
