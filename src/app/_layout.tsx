import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/theme';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.bg },
          headerTintColor: colors.text,
          headerShadowVisible: false,
          headerTitleStyle: { fontWeight: '600' },
          contentStyle: { backgroundColor: colors.bg },
        }}>
        <Stack.Screen name="index" options={{ title: 'BatMonitor' }} />
        <Stack.Screen name="add-device" options={{ title: 'Añadir batería', presentation: 'modal' }} />
        <Stack.Screen name="device/[id]" options={{ title: 'Batería' }} />
      </Stack>
    </>
  );
}
